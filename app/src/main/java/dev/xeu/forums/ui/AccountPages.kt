package dev.xeu.forums.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.xeu.forums.Nav
import dev.xeu.forums.data.BASE
import dev.xeu.forums.data.ForumRepository
import dev.xeu.forums.data.Http
import kotlinx.coroutines.launch

@Composable
private fun NeedsLogin(nav: Nav) {
    EmptyState(Icons.Outlined.Lock, "Log in required", "Log in to your Xiaomi.EU account to see this.") {
        androidx.compose.material3.Button(onClick = { nav.login() }, modifier = Modifier.padding(top = 8.dp)) {
            Text("Log in")
        }
    }
}

@Composable
private fun UnreadDot() =
    Box(Modifier.size(10.dp).background(MaterialTheme.colorScheme.primary, CircleShape))

@Composable
fun AlertsScreen(nav: Nav) {
    val scope = rememberCoroutineScope()
    val loader = rememberLoader("alerts") { ForumRepository.alerts() }
    ScreenScaffold(
        title = "Alerts",
        large = true,
        onBack = { nav.back() },
        actions = {
            IconButton(onClick = { scope.launch { runCatching { ForumRepository.markAlertsRead() }; loader.reload() } }) {
                Icon(Icons.Outlined.DoneAll, "Mark all read")
            }
        },
    ) { mod ->
        if (!Http.isLoggedIn()) { Box(mod) { NeedsLogin(nav) }; return@ScreenScaffold }
        when (val s = loader.state) {
            Load.Loading -> LoadingBox(mod)
            is Load.Err -> ErrorBox(s.message, loader.reload, mod)
            is Load.Ok -> RefreshBox(loader.refreshing, loader.reload, mod) {
                LazyColumn(Modifier.fillMaxSize()) {
                    if (s.value.isEmpty()) item {
                        EmptyState(Icons.Outlined.Notifications, "No alerts yet",
                            "Replies, quotes, mentions and reactions to your posts show up here.")
                    }
                    items(s.value, key = { it.id }) { a ->
                        Surface(
                            onClick = { a.link?.let(nav::link) },
                            enabled = a.link != null,
                            shape = MaterialTheme.shapes.large,
                            color = if (a.unread) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            else MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        ) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                AvatarImage(a.avatarUrl, a.text, 42)
                                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                    Text(a.text, style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (a.unread) FontWeight.SemiBold else FontWeight.Normal,
                                        maxLines = 4, overflow = TextOverflow.Ellipsis)
                                    Text(relativeTime(a.epoch), style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 4.dp))
                                }
                                if (a.unread) UnreadDot()
                            }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
fun ConversationsScreen(nav: Nav) {
    val loader = rememberLoader("convos") { ForumRepository.conversations() }
    ScreenScaffold(
        title = "Direct messages",
        large = true,
        onBack = { nav.back() },
        floatingActionButton = {
            if (Http.isLoggedIn()) ExtendedFloatingActionButton(
                onClick = { nav.web(BASE + "conversations/add", "New message") },
                icon = { Icon(Icons.Outlined.Edit, null) },
                text = { Text("New message") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { mod ->
        if (!Http.isLoggedIn()) { Box(mod) { NeedsLogin(nav) }; return@ScreenScaffold }
        when (val s = loader.state) {
            Load.Loading -> LoadingBox(mod)
            is Load.Err -> ErrorBox(s.message, loader.reload, mod)
            is Load.Ok -> RefreshBox(loader.refreshing, loader.reload, mod) {
                LazyColumn(Modifier.fillMaxSize()) {
                    if (s.value.isEmpty()) item {
                        EmptyState(Icons.Outlined.Mail, "No conversations", "Private messages with other members appear here.")
                    }
                    items(s.value, key = { it.id }) { c ->
                        Surface(
                            onClick = { nav.web(c.url, c.title) },
                            shape = MaterialTheme.shapes.large,
                            color = if (c.unread) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            else MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        ) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                AvatarImage(null, c.lastSender.ifBlank { c.participants.firstOrNull() ?: c.title }, 46)
                                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            c.participants.joinToString().ifBlank { c.lastSender },
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text(relativeTime(c.lastEpoch), style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline)
                                    }
                                    Text(c.title, style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (c.unread) FontWeight.SemiBold else FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                if (c.unread) UnreadDot()
                            }
                        }
                    }
                    item { Spacer(Modifier.height(96.dp)) }
                }
            }
        }
    }
}

@Composable
fun WatchedScreen(nav: Nav) {
    var page by rememberSaveable { mutableIntStateOf(1) }
    val loader = rememberLoader("watched", page) { ForumRepository.watchedThreads(page) }
    ScreenScaffold(title = "Watched threads", large = true, onBack = { nav.back() }) { mod ->
        if (!Http.isLoggedIn()) { Box(mod) { NeedsLogin(nav) }; return@ScreenScaffold }
        when (val s = loader.state) {
            Load.Loading -> LoadingBox(mod)
            is Load.Err -> ErrorBox(s.message, loader.reload, mod)
            is Load.Ok -> RefreshBox(loader.refreshing, loader.reload, mod) {
                val f = s.value
                LazyColumn(Modifier.fillMaxSize()) {
                    if (f.threads.isEmpty()) item {
                        EmptyState(Icons.Outlined.Visibility, "Not watching anything",
                            "Watch a thread to follow its replies here and get alerts.")
                    }
                    items(f.threads, key = { it.id }) { t ->
                        ThreadCard(t, onOpen = { nav.thread(t.url, 1) }, onLatest = {
                            nav.thread(ForumRepository.baseUrl(t.url) + "unread", 0)
                        })
                    }
                    item { PagerBar(f.page, f.totalPages, { page = it }) }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}
