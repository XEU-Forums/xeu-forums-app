package dev.xeu.forums.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.xeu.forums.Nav
import dev.xeu.forums.data.Device
import dev.xeu.forums.data.FeedItem
import dev.xeu.forums.data.ForumRepository
import dev.xeu.forums.data.Prefs
import kotlinx.coroutines.launch

@Composable
fun DevicesScreen(nav: Nav) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val devices by prefs.devices.collectAsState(initial = null)
    var filter by rememberSaveable { mutableStateOf<Int?>(null) }
    val accents = LocalAccents.current

    Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0)) { pad ->
        val list = devices
        val shown = list.orEmpty().filter { filter == null || it.forumId == filter }
        val loader = rememberLoader(shown.map { it.forumId }) {
            if (shown.isEmpty()) emptyList() else ForumRepository.deviceFeed(shown)
        }
        RefreshBox(loader.refreshing, loader.reload, Modifier.padding(pad).fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    HeroHeader {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("YOUR PHONES", style = SectionLabelStyle, color = accents.onHero.copy(alpha = 0.8f))
                                Text("My devices", style = MaterialTheme.typography.headlineMedium, color = accents.onHero)
                            }
                            IconButton(
                                onClick = { nav.devicePicker() },
                                colors = IconButtonDefaults.iconButtonColors(containerColor = Color.White.copy(alpha = 0.18f)),
                            ) { Icon(Icons.Outlined.Edit, "Choose devices", tint = accents.onHero) }
                        }
                        Text(
                            if (list.isNullOrEmpty()) "Follow the phones you own"
                            else "Latest from ${list.size} device forum${if (list.size == 1) "" else "s"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = accents.onHero.copy(alpha = 0.9f),
                        )
                        Button(
                            onClick = { nav.roms() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Brand.OrangeDeep),
                            modifier = Modifier.padding(top = 14.dp),
                        ) {
                            Icon(Icons.Outlined.SystemUpdate, null, Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text("Latest ROM builds")
                        }
                        if (!list.isNullOrEmpty()) Row(
                            Modifier.padding(top = 12.dp).horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            val chipColors = FilterChipDefaults.filterChipColors(
                                containerColor = Color.White.copy(alpha = 0.16f), labelColor = Color.White,
                                selectedContainerColor = Color.White, selectedLabelColor = Brand.OrangeDeep,
                            )
                            FilterChip(filter == null, { filter = null }, { Text("All") }, colors = chipColors, border = null)
                            list.forEach { d ->
                                FilterChip(filter == d.forumId, { filter = d.forumId }, {
                                    Text(d.codenames.firstOrNull() ?: d.name)
                                }, colors = chipColors, border = null)
                            }
                        }
                    }
                }
                when {
                    list == null -> item { LoadingBox(Modifier.height(300.dp)) }
                    list.isEmpty() -> item {
                        EmptyState(
                            Icons.Outlined.PhoneAndroid, "No devices yet",
                            "Pick the phones you own to get a feed of their forums and alerts for new Xiaomi.EU builds.",
                        ) {
                            Button(onClick = { nav.devicePicker() }, modifier = Modifier.padding(top = 8.dp)) {
                                Text("Choose devices")
                            }
                        }
                    }
                    else -> when (val s = loader.state) {
                        Load.Loading -> item { LoadingBox(Modifier.height(300.dp)) }
                        is Load.Err -> item { ErrorBox(s.message, loader.reload, Modifier.height(320.dp)) }
                        is Load.Ok -> {
                            if (s.value.isEmpty()) item {
                                EmptyState(Icons.Outlined.Smartphone, "All quiet", "Nothing new in these forums.")
                            }
                            item { Spacer(Modifier.height(8.dp)) }
                            items(s.value, key = { it.link }) { FeedCard(it) { nav.link(it.link) } }
                            item { Spacer(Modifier.height(24.dp)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FeedCard(item: FeedItem, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Pill(item.source, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
            Text(
                item.title.substringAfterLast('/'),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 3, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (item.author.isNotBlank()) {
                    AvatarImage(null, item.author, 20)
                    Text(item.author, style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp))
                }
                Spacer(Modifier.weight(1f))
                Text(relativeTime(item.epoch), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
fun DevicePickerScreen(nav: Nav) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val scope = rememberCoroutineScope()
    val chosen by prefs.devices.collectAsState(initial = emptyList())
    val loader = rememberLoader("devices") { ForumRepository.allDeviceForums() }
    var query by rememberSaveable { mutableStateOf("") }

    ScreenScaffold(
        title = "Choose devices",
        subtitle = if (chosen.isEmpty()) null else "${chosen.size} selected",
        onBack = { nav.back() },
    ) { mod ->
        when (val s = loader.state) {
            Load.Loading -> LoadingBox(mod)
            is Load.Err -> ErrorBox(s.message, loader.reload, mod)
            is Load.Ok -> Column(mod) {
                TextField(
                    value = query, onValueChange = { query = it },
                    placeholder = { Text("Search model or codename") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null, tint = MaterialTheme.colorScheme.primary) },
                    singleLine = true,
                    shape = CircleShape,
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                )
                val q = query.trim().lowercase()
                val nodes = s.value.filter {
                    q.isEmpty() || it.title.lowercase().contains(q) || it.codenames.any { c -> c.contains(q) }
                }
                LazyColumn {
                    items(nodes, key = { it.url }) { node ->
                        val selected = chosen.any { it.forumId == node.id }
                        Surface(
                            onClick = {
                                scope.launch { prefs.toggleDevice(Device(node.id, node.title, node.url, node.codenames)) }
                            },
                            shape = MaterialTheme.shapes.large,
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
                        ) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconTile(Icons.Outlined.Smartphone)
                                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                                    Text(node.title, style = MaterialTheme.typography.titleMedium,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold)
                                    Text(node.codenames.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(
                                    if (selected) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                                    if (selected) "Selected" else "Not selected",
                                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                )
                            }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}
