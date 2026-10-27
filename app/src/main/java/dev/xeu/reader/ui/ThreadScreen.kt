package dev.xeu.reader.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.xeu.reader.Nav
import dev.xeu.reader.data.ForumRepository
import dev.xeu.reader.data.Http
import dev.xeu.reader.data.Post

@Composable
fun ThreadScreen(url: String, initialPage: Int, nav: Nav) {
    val context = LocalContext.current
    var reqUrl by rememberSaveable(url) { mutableStateOf(url) }
    var page by rememberSaveable(url) { mutableIntStateOf(initialPage) }
    val loader = rememberLoader(reqUrl, page) { ForumRepository.thread(reqUrl, page) }
    val data = (loader.state as? Load.Ok)?.value
    val listState = rememberLazyListState()
    val loggedIn = Http.isLoggedIn()

    // Jump to a specific post when opened from a "post-123" / "goto/post?id=123" link.
    val targetPost = remember(url) {
        Regex("""(?:post-|[?&]id=)(\d+)""").findAll(url).lastOrNull()?.groupValues?.get(1)?.toIntOrNull()
    }
    LaunchedEffect(data) {
        if (data == null) return@LaunchedEffect
        val idx = data.posts.indexOfFirst { it.id == targetPost }
        // +1 for the pager row at the top
        listState.scrollToItem(if (idx >= 0) idx + 1 else 0)
    }

    fun goPage(p: Int) {
        data?.let { reqUrl = ForumRepository.baseUrl(it.url) }
        page = p
    }

    ScreenScaffold(
        title = data?.title ?: "Thread",
        onBack = { nav.back() },
        actions = {
            IconButton(onClick = loader.reload) { Icon(Icons.Filled.Refresh, "Refresh") }
            IconButton(onClick = {
                val share = Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, data?.url ?: url)
                context.startActivity(Intent.createChooser(share, null))
            }) { Icon(Icons.Filled.Share, "Share") }
            IconButton(onClick = { openExternal(context, data?.url ?: url) }) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, "Open in browser")
            }
        },
        floatingActionButton = {
            when {
                data == null -> {}
                data.canReply -> ExtendedFloatingActionButton(
                    onClick = { nav.reply(data.url) },
                    icon = { Icon(Icons.AutoMirrored.Filled.Reply, null) },
                    text = { Text("Reply") },
                )
                !loggedIn && !data.locked -> ExtendedFloatingActionButton(
                    onClick = { nav.login() },
                    icon = { Icon(Icons.AutoMirrored.Filled.Reply, null) },
                    text = { Text("Log in to reply") },
                )
            }
        },
    ) { mod ->
        when (val s = loader.state) {
            Load.Loading -> LoadingBox(mod)
            is Load.Err -> ErrorBox(s.message, loader.reload, mod)
            is Load.Ok -> {
                val t = s.value
                LazyColumn(mod, state = listState) {
                    item { PagerBar(t.page, t.totalPages, ::goPage) }
                    items(t.posts, key = { it.id }) { p ->
                        PostCard(
                            p,
                            highlighted = p.id == targetPost,
                            onLink = { nav.link(it) },
                            onImage = { openExternal(context, it) },
                            onQuote = if (t.canReply) {
                                { nav.reply(t.url, quoteFor(p)) }
                            } else null,
                        )
                    }
                    item { PagerBar(t.page, t.totalPages, ::goPage) }
                    if (t.locked) item {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Lock, null, Modifier.size(16.dp))
                            Spacer(Modifier.size(8.dp))
                            Text("Not open for further replies.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    item { Spacer(Modifier.size(88.dp)) }
                }
            }
        }
    }
}

private fun quoteFor(p: Post): String {
    val plain = org.jsoup.Jsoup.parse(p.html).text().take(600)
    return "[QUOTE=\"${p.author}, post: ${p.id}\"]$plain[/QUOTE]\n"
}

@Composable
fun PostCard(
    p: Post,
    highlighted: Boolean,
    onLink: (String) -> Unit,
    onImage: (String) -> Unit,
    onQuote: (() -> Unit)?,
) {
    ElevatedCard(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Column(
            Modifier
                .then(if (highlighted) Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)) else Modifier)
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(p)
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        p.author,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (p.isStaff) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        listOf(p.userTitle, relativeTime(p.epoch)).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(p.number, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.size(8.dp))
            HtmlText(p.html, onLink)
            p.images.forEach { img ->
                AsyncImage(
                    model = img,
                    contentDescription = "Image in post",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                        .heightIn(max = 480.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onImage(img) },
                )
            }
            if (onQuote != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onQuote) {
                        Icon(Icons.Filled.FormatQuote, null, Modifier.size(18.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Quote")
                    }
                }
            }
        }
    }
}

@Composable
private fun Avatar(p: Post) {
    val mod = Modifier.size(36.dp).clip(CircleShape)
    if (p.avatarUrl != null) {
        AsyncImage(model = p.avatarUrl, contentDescription = null, modifier = mod, contentScale = ContentScale.Crop)
    } else {
        Box(mod.background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
            Text(p.author.take(1).uppercase(), color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}
