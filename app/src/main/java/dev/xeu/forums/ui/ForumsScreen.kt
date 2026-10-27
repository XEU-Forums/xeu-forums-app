package dev.xeu.forums.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import dev.xeu.forums.Nav
import dev.xeu.forums.data.ForumIndex
import dev.xeu.forums.data.ForumRepository
import dev.xeu.forums.data.Node

@Composable
fun ForumsScreen(nav: Nav) {
    val loader = rememberLoader("index") { ForumRepository.index() }
    Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0)) { pad ->
        RefreshBox(loader.refreshing, loader.reload, Modifier.padding(pad).fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize()) {
                item(key = "hero") { HomeHero((loader.state as? Load.Ok)?.value, nav) }
                when (val s = loader.state) {
                    Load.Loading -> item { LoadingBox(Modifier.height(320.dp)) }
                    is Load.Err -> item { ErrorBox(s.message, loader.reload, Modifier.height(360.dp)) }
                    is Load.Ok -> {
                        s.value.sections.forEachIndexed { i, section ->
                            item(key = "h-$i") { SectionLabel(section.title) }
                            item(key = "s-$i") {
                                SectionCard {
                                    section.nodes.forEachIndexed { j, node ->
                                        if (j > 0) CardDivider()
                                        NodeRow(node) { nav.link(it) }
                                    }
                                }
                            }
                        }
                        item(key = "foot") {
                            Text(
                                "Unofficial app · Not affiliated with Xiaomi or Xiaomi.EU",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHero(index: ForumIndex?, nav: Nav) {
    val accents = LocalAccents.current
    HeroHeader {
        Text(
            "UNOFFICIAL APP",
            style = SectionLabelStyle,
            color = accents.onHero.copy(alpha = 0.8f),
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            "Xiaomi.EU Forums",
            style = MaterialTheme.typography.headlineLarge,
            color = accents.onHero,
            modifier = Modifier.padding(top = 2.dp),
        )
        // Search pill
        Surface(
            onClick = { nav.search() },
            shape = CircleShape,
            color = Color.White,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(52.dp),
        ) {
            Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Search, null, tint = Brand.OrangeDeep)
                Text(
                    "Search threads and posts",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFF6B5A52),
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
        // Forum statistics
        val stats = index?.stats.orEmpty().take(3)
        if (stats.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                stats.forEach { (k, v) ->
                    Column {
                        Text(v, style = MaterialTheme.typography.titleLarge, color = accents.onHero, fontWeight = FontWeight.Bold)
                        Text(k, style = MaterialTheme.typography.labelMedium, color = accents.onHero.copy(alpha = 0.8f))
                    }
                }
            }
        }
    }
    // Quick actions
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, top = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        QuickChip(Icons.Outlined.Bolt, "New posts") { nav.forum(ForumRepository.NEW_POSTS) }
        QuickChip(Icons.Outlined.SystemUpdate, "ROM releases") { nav.forum(dev.xeu.forums.data.RELEASE_FORUM) }
        QuickChip(Icons.Outlined.PhoneAndroid, "My devices") { nav.tab("devices") }
    }
}

@Composable
private fun QuickChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, null, Modifier.size(AssistChipDefaults.IconSize), tint = MaterialTheme.colorScheme.primary) },
        shape = CircleShape,
    )
}

@Composable
fun NodeRow(node: Node, onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().clickable { onOpen(node.url) }.padding(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                IconTile(nodeIcon(node.title))
                if (node.hasNew) Box(
                    Modifier.align(Alignment.TopEnd).size(10.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
            Column(Modifier.weight(1f).padding(start = 14.dp, end = 8.dp)) {
                Text(
                    node.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (node.hasNew) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = listOfNotNull(
                    node.threads.takeIf { it.isNotBlank() }?.let { "$it threads" },
                    node.messages.takeIf { it.isNotBlank() }?.let { "$it messages" },
                ).joinToString(" · ")
                if (node.codenames.isEmpty() && node.description.isNotBlank()) {
                    Text(node.description, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 2.dp))
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
        }
        if (node.codenames.isNotEmpty()) {
            Row(
                Modifier.padding(start = 54.dp, top = 8.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                node.codenames.forEach {
                    Pill(it, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }
        if (node.subNodes.isNotEmpty()) {
            Row(
                Modifier.padding(start = 54.dp, top = 8.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                node.subNodes.forEach { sub ->
                    Surface(
                        onClick = { onOpen(sub.url) },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Text(sub.title, style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                    }
                }
            }
        }
    }
}
