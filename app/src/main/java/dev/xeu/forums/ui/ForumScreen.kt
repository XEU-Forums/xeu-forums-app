package dev.xeu.forums.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import dev.xeu.forums.Nav
import dev.xeu.forums.data.ForumRepository
import dev.xeu.forums.data.ThreadItem

fun shareLink(context: android.content.Context, url: String, title: String? = null) {
    val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
    title?.let { i.putExtra(Intent.EXTRA_SUBJECT, it) }
    context.startActivity(Intent.createChooser(i, null))
}

@Composable
fun ForumScreen(url: String, nav: Nav) {
    val context = LocalContext.current
    var page by rememberSaveable(url) { mutableIntStateOf(1) }
    val loader = rememberLoader(url, page) { ForumRepository.forum(url, page) }
    val data = (loader.state as? Load.Ok)?.value
    val listState = rememberLazyListState()
    LaunchedEffect(page) { listState.scrollToItem(0) }

    ScreenScaffold(
        title = data?.title ?: "Forum",
        large = true,
        onBack = { nav.back() },
        actions = {
            IconButton(onClick = { shareLink(context, data?.url ?: url, data?.title) }) {
                Icon(Icons.Outlined.Share, "Share")
            }
        },
        floatingActionButton = {
            if (data?.canPostThread == true) {
                ExtendedFloatingActionButton(
                    onClick = { nav.newThread(data.url) },
                    icon = { Icon(Icons.Outlined.Edit, null) },
                    text = { Text("New thread") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) { mod ->
        when (val s = loader.state) {
            Load.Loading -> LoadingBox(mod)
            is Load.Err -> ErrorBox(s.message, loader.reload, mod)
            is Load.Ok -> RefreshBox(loader.refreshing, loader.reload, mod) {
                val f = s.value
                LazyColumn(Modifier.fillMaxSize(), state = listState) {
                    if (f.subNodes.isNotEmpty() && page == 1) {
                        item { SectionLabel("Sub-forums") }
                        item {
                            SectionCard {
                                f.subNodes.forEachIndexed { i, n ->
                                    if (i > 0) CardDivider()
                                    NodeRow(n) { u -> nav.link(u) }
                                }
                            }
                        }
                    }
                    if (f.threads.isNotEmpty()) {
                        if (f.subNodes.isNotEmpty() && page == 1) item { SectionLabel("Threads") }
                        items(f.threads, key = { "t-${it.id}" }) { t ->
                            ThreadCard(t, onOpen = { nav.thread(t.url, 1) }, onLatest = {
                                nav.thread(ForumRepository.baseUrl(t.url) + "latest", 0)
                            })
                        }
                        item { PagerBar(f.page, f.totalPages, { page = it }) }
                    } else if (f.subNodes.isEmpty()) {
                        item { EmptyState(Icons.Outlined.Forum, "Nothing here yet", "This forum has no threads.") }
                    }
                    item { Spacer(Modifier.height(88.dp)) }
                }
            }
        }
    }
}

@Composable
fun ThreadCard(t: ThreadItem, onOpen: () -> Unit, onLatest: () -> Unit) {
    Surface(
        onClick = onOpen,
        shape = MaterialTheme.shapes.large,
        color = if (t.sticky) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 6.dp)) {
            val badges = t.sticky || t.locked || t.prefix.isNotBlank()
            if (badges) Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (t.sticky) Pill("Pinned", icon = Icons.Outlined.PushPin)
                if (t.locked) Pill("Closed", MaterialTheme.colorScheme.surfaceContainerHighest,
                    MaterialTheme.colorScheme.onSurfaceVariant, Icons.Outlined.Lock)
                t.prefix.split(Regex("\\s{2,}")).filter { it.isNotBlank() }.forEach {
                    Pill(it, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                }
            }
            Text(
                t.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (t.unread) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                AvatarImage(null, t.author, 22)
                Text(
                    t.author,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 6.dp).weight(1f, fill = false),
                )
                if (t.lastPostEpoch > 0) Text(
                    " · ${relativeTime(t.lastPostEpoch)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
                Spacer(Modifier.weight(1f))
                if (t.replies.isNotBlank()) Stat(Icons.Outlined.ChatBubbleOutline, t.replies)
                if (t.views.isNotBlank()) {
                    Spacer(Modifier.size(10.dp))
                    Stat(Icons.Outlined.Visibility, t.views)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (t.lastPoster.isNotBlank() && t.lastPoster != t.author) Text(
                    "Last reply by ${t.lastPoster}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                ) else Spacer(Modifier.weight(1f))
                TextButton(onClick = onLatest) { Text("Jump to latest") }
            }
        }
    }
}
