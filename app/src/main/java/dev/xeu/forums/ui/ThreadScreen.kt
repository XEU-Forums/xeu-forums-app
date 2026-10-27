package dev.xeu.forums.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Verified
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
import dev.xeu.forums.Nav
import dev.xeu.forums.data.ForumRepository
import dev.xeu.forums.data.Http
import dev.xeu.forums.data.Post

@Composable
fun ThreadScreen(url: String, initialPage: Int, nav: Nav) {
    val context = LocalContext.current
    var reqUrl by rememberSaveable(url) { mutableStateOf(url) }
    var page by rememberSaveable(url) { mutableIntStateOf(initialPage) }
    val loader = rememberLoader(reqUrl, page) { ForumRepository.thread(reqUrl, page) }
    val data = (loader.state as? Load.Ok)?.value
    val listState = rememberLazyListState()
    val loggedIn = Http.isLoggedIn()
    var viewer by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }

    // Jump to a specific post when opened from a "post-123" / "goto/post?id=123" link.
    val targetPost = remember(url) {
        Regex("""(?:post-|[?&]id=)(\d+)""").findAll(url).lastOrNull()?.groupValues?.get(1)?.toIntOrNull()
    }
    var scrolledFor by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(data) {
        val d = data ?: return@LaunchedEffect
        val key = "${d.url}#${d.page}"
        if (scrolledFor == key) return@LaunchedEffect
        scrolledFor = key
        val idx = d.posts.indexOfFirst { it.id == targetPost }
        listState.scrollToItem(if (idx >= 0) idx + 1 else 0)
    }

    fun goPage(p: Int) {
        data?.let { reqUrl = ForumRepository.baseUrl(it.url) }
        page = p
    }

    ScreenScaffold(
        title = data?.title ?: "Thread",
        subtitle = data?.let { if (it.totalPages > 1) "Page ${it.page} of ${it.totalPages}" else null },
        onBack = { nav.back() },
        actions = {
            IconButton(onClick = { shareLink(context, data?.url ?: url, data?.title) }) {
                Icon(Icons.Outlined.Share, "Share")
            }
        },
        floatingActionButton = {
            when {
                data == null -> {}
                data.canReply -> ExtendedFloatingActionButton(
                    onClick = { nav.reply(data.url) },
                    icon = { Icon(Icons.AutoMirrored.Outlined.Reply, null) },
                    text = { Text("Reply") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
                !loggedIn && !data.locked -> ExtendedFloatingActionButton(
                    onClick = { nav.login() },
                    icon = { Icon(Icons.AutoMirrored.Outlined.Reply, null) },
                    text = { Text("Log in to reply") },
                )
            }
        },
    ) { mod ->
        when (val s = loader.state) {
            Load.Loading -> LoadingBox(mod)
            is Load.Err -> ErrorBox(s.message, loader.reload, mod)
            is Load.Ok -> RefreshBox(loader.refreshing, loader.reload, mod) {
                val t = s.value
                val opAuthor = if (t.page == 1) t.posts.firstOrNull()?.author else null
                LazyColumn(Modifier.fillMaxSize(), state = listState) {
                    item { PagerBar(t.page, t.totalPages, ::goPage) }
                    items(t.posts, key = { it.id }) { p ->
                        PostCard(
                            p,
                            highlighted = p.id == targetPost,
                            isOp = p.author == opAuthor,
                            onLink = { nav.link(it) },
                            onImage = { i -> viewer = p.images to i },
                            onQuote = if (t.canReply) { { nav.reply(t.url, quoteFor(p)) } } else null,
                        )
                    }
                    item { PagerBar(t.page, t.totalPages, ::goPage) }
                    if (t.locked) item {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.Lock, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
                            Spacer(Modifier.size(8.dp))
                            Text("Not open for further replies", style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.outline)
                        }
                    }
                    item { Spacer(Modifier.height(96.dp)) }
                }
            }
        }
    }

    viewer?.let { (imgs, start) -> ImageViewer(imgs, start) { viewer = null } }
}

private fun quoteFor(p: Post): String {
    val plain = org.jsoup.Jsoup.parse(p.html).text().take(600)
    return "[QUOTE=\"${p.author}, post: ${p.id}\"]$plain[/QUOTE]\n"
}

@Composable
fun PostCard(
    p: Post,
    highlighted: Boolean,
    isOp: Boolean,
    onLink: (String) -> Unit,
    onImage: (Int) -> Unit,
    onQuote: (() -> Unit)?,
) {
    val accents = LocalAccents.current
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp)
            .then(
                if (highlighted) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.large)
                else Modifier
            ),
    ) {
        Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AvatarImage(p.avatarUrl, p.author, 40)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            p.author,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (p.isStaff) accents.staff else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (p.isStaff) Icon(Icons.Outlined.Verified, "Staff", Modifier.size(16.dp), tint = accents.staff)
                        if (isOp) Pill("OP")
                    }
                    Text(
                        listOf(p.userTitle, relativeTime(p.epoch)).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(p.number, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
            }
            Spacer(Modifier.height(10.dp))
            HtmlText(p.html, onLink)
            if (p.images.isNotEmpty()) ImageStrip(p.images, onImage)
            if (onQuote != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onQuote) {
                        Icon(Icons.Outlined.FormatQuote, null, Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Quote")
                    }
                }
            } else Spacer(Modifier.height(10.dp))
        }
    }
}

/** One image full width; several in a two-column grid. */
@Composable
private fun ImageStrip(images: List<String>, onImage: (Int) -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    if (images.size == 1) {
        AsyncImage(
            model = images[0],
            contentDescription = "Image in post",
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.padding(top = 10.dp).fillMaxWidth().heightIn(max = 420.dp).clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest).clickable { onImage(0) },
        )
        return
    }
    Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        images.chunked(2).forEachIndexed { row, pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pair.forEachIndexed { col, img ->
                    val index = row * 2 + col
                    Box(Modifier.weight(1f).aspectRatio(1f).clip(shape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest).clickable { onImage(index) }) {
                        AsyncImage(img, "Image in post", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
