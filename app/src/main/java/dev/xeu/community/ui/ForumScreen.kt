package dev.xeu.community.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.xeu.community.Nav
import dev.xeu.community.data.ForumRepository
import dev.xeu.community.data.ThreadItem

@Composable
fun ForumScreen(url: String, nav: Nav) {
    val context = LocalContext.current
    var page by rememberSaveable(url) { mutableIntStateOf(1) }
    val loader = rememberLoader(url, page) { ForumRepository.forum(url, page) }
    val data = (loader.state as? Load.Ok)?.value

    ScreenScaffold(
        title = data?.title ?: "Forum",
        onBack = { nav.back() },
        actions = {
            IconButton(onClick = loader.reload) { Icon(Icons.Filled.Refresh, "Refresh") }
            IconButton(onClick = { openExternal(context, url) }) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, "Open in browser")
            }
        },
        floatingActionButton = {
            if (data?.canPostThread == true) {
                ExtendedFloatingActionButton(
                    onClick = { nav.newThread(data.url) },
                    icon = { Icon(Icons.Filled.Edit, null) },
                    text = { Text("New thread") },
                )
            }
        },
    ) { mod ->
        when (val s = loader.state) {
            Load.Loading -> LoadingBox(mod)
            is Load.Err -> ErrorBox(s.message, loader.reload, mod)
            is Load.Ok -> {
                val f = s.value
                LazyColumn(mod) {
                    if (f.subNodes.isNotEmpty() && page == 1) {
                        item { SectionHeader("Sub-forums") }
                        items(f.subNodes, key = { "sub-${it.url}" }) { NodeRow(it) { u -> nav.link(u) } }
                    }
                    if (f.threads.isNotEmpty()) {
                        item { PagerBar(f.page, f.totalPages) { page = it } }
                        items(f.threads, key = { "t-${it.id}" }) { t ->
                            ThreadRow(t, onOpen = { nav.thread(t.url, 1) }, onLatest = {
                                nav.thread(ForumRepository.baseUrl(t.url) + "latest", 0)
                            })
                        }
                        item { PagerBar(f.page, f.totalPages) { page = it } }
                    } else if (f.subNodes.isEmpty()) {
                        item {
                            Text("No threads here.", modifier = Modifier.padding(24.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ThreadRow(t: ThreadItem, onOpen: () -> Unit, onLatest: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (t.sticky) {
                Icon(Icons.Filled.PushPin, "Sticky", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(4.dp))
            }
            if (t.locked) {
                Icon(Icons.Filled.Lock, "Closed", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.size(4.dp))
            }
            if (t.prefix.isNotBlank()) {
                Text(
                    t.prefix,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
        }
        Text(
            t.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (t.unread) FontWeight.Bold else FontWeight.Medium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
            Text(
                buildString {
                    append(t.author)
                    if (t.replies.isNotBlank()) append(" · ${t.replies} replies")
                    if (t.lastPostEpoch > 0) append(" · last ${relativeTime(t.lastPostEpoch)}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            AssistChip(onClick = onLatest, label = { Text("Latest") })
        }
    }
    HorizontalDivider()
}
