package dev.xeu.community.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.xeu.community.Nav
import dev.xeu.community.data.Device
import dev.xeu.community.data.FeedItem
import dev.xeu.community.data.ForumRepository
import dev.xeu.community.data.Prefs
import kotlinx.coroutines.launch

@Composable
fun DevicesScreen(nav: Nav) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val devices by prefs.devices.collectAsState(initial = null)
    var filter by rememberSaveable { mutableStateOf<Int?>(null) }

    ScreenScaffold(
        title = "My devices",
        actions = {
            IconButton(onClick = { nav.devicePicker() }) { Icon(Icons.Filled.Edit, "Choose devices") }
        },
    ) { mod ->
        val list = devices
        when {
            list == null -> LoadingBox(mod)
            list.isEmpty() -> Column(
                mod.padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Pick the phones you own to get a feed of their forums and alerts for new xiaomi.eu builds.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Button(onClick = { nav.devicePicker() }, modifier = Modifier.padding(top = 16.dp)) {
                    Text("Choose devices")
                }
            }
            else -> {
                val shown = list.filter { filter == null || it.forumId == filter }
                val loader = rememberLoader(shown.map { it.forumId }) { ForumRepository.deviceFeed(shown) }
                Column(mod) {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilterChip(filter == null, { filter = null }, { Text("All") })
                        list.forEach { d ->
                            FilterChip(filter == d.forumId, { filter = d.forumId }, {
                                Text(d.codenames.firstOrNull() ?: d.name)
                            })
                        }
                        IconButton(onClick = loader.reload) { Icon(Icons.Filled.Refresh, "Refresh") }
                    }
                    when (val s = loader.state) {
                        Load.Loading -> LoadingBox()
                        is Load.Err -> ErrorBox(s.message, loader.reload)
                        is Load.Ok -> LazyColumn {
                            if (s.value.isEmpty()) item {
                                Text("Nothing new in these forums.", modifier = Modifier.padding(24.dp))
                            }
                            items(s.value, key = { it.link }) { item ->
                                FeedRow(item) { nav.link(item.link) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FeedRow(item: FeedItem, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(item.title.substringAfterLast('/'), style = MaterialTheme.typography.titleSmall, maxLines = 3,
            overflow = TextOverflow.Ellipsis)
        Text(
            listOf(item.source, item.author, relativeTime(item.epoch)).filter { it.isNotBlank() }.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider()
}

@Composable
fun DevicePickerScreen(nav: Nav) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val scope = rememberCoroutineScope()
    val chosen by prefs.devices.collectAsState(initial = emptyList())
    val loader = rememberLoader("devices") { ForumRepository.allDeviceForums() }
    var query by rememberSaveable { mutableStateOf("") }

    ScreenScaffold(title = "Choose your devices", onBack = { nav.back() }) { mod ->
        when (val s = loader.state) {
            Load.Loading -> LoadingBox(mod)
            is Load.Err -> ErrorBox(s.message, loader.reload, mod)
            is Load.Ok -> Column(mod) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    label = { Text("Search model or codename") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                )
                val q = query.trim().lowercase()
                val nodes = s.value.filter {
                    q.isEmpty() || it.title.lowercase().contains(q) || it.codenames.any { c -> c.contains(q) }
                }
                LazyColumn {
                    items(nodes, key = { it.url }) { node ->
                        val selected = chosen.any { it.forumId == node.id }
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                scope.launch { prefs.toggleDevice(Device(node.id, node.title, node.url, node.codenames)) }
                            }.padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = selected, onCheckedChange = null)
                            Column(Modifier.padding(start = 8.dp)) {
                                Text(node.title, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    node.codenames.joinToString(", "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
