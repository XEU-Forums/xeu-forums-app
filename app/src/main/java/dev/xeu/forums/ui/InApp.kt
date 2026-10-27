package dev.xeu.forums.ui

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import dev.xeu.forums.Nav
import dev.xeu.forums.data.ForumRepository
import dev.xeu.forums.data.Http
import dev.xeu.forums.data.SearchPage

// ---------------------------------------------------------------- downloads

object Downloads {
    /** Saves a file to Downloads with the system download manager, carrying the forum session. */
    fun start(context: Context, url: String, contentDisposition: String? = null, mime: String? = null) {
        runCatching {
            val name = URLUtil.guessFileName(url, contentDisposition, mime)
            val req = DownloadManager.Request(Uri.parse(url))
                .setTitle(name)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
                .addRequestHeader("User-Agent", Http.userAgent)
            CookieManager.getInstance().getCookie(url)?.let { req.addRequestHeader("Cookie", it) }
            mime?.let { req.setMimeType(it) }
            context.getSystemService(DownloadManager::class.java).enqueue(req)
            Toast.makeText(context, "Downloading $name", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(context, "Couldn't start the download", Toast.LENGTH_SHORT).show()
        }
    }
}

fun copyLink(context: Context, url: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Link", url))
    Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
}

// ---------------------------------------------------------------- image viewer

@Composable
fun ImageViewer(images: List<String>, start: Int, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val pager = rememberPagerState(initialPage = start.coerceIn(0, (images.size - 1).coerceAtLeast(0))) { images.size }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(pager, Modifier.fillMaxSize()) { i -> ZoomableImage(images[i]) }
            Row(
                Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Close", tint = Color.White) }
                Text(
                    if (images.size > 1) "${pager.currentPage + 1} / ${images.size}" else "",
                    color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { shareLink(context, images[pager.currentPage]) }) {
                    Icon(Icons.Outlined.Share, "Share", tint = Color.White)
                }
                IconButton(onClick = { Downloads.start(context, images[pager.currentPage]) }) {
                    Icon(Icons.Outlined.Download, "Save", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun ZoomableImage(url: String) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        offset = if (scale > 1f) offset + pan else Offset.Zero
    }
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f
                })
            }
            .transformable(state, canPan = { scale > 1f })
            .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
    )
}

// ---------------------------------------------------------------- in-app browser

/**
 * In-app browser for everything that isn't rendered natively: account pages, conversations,
 * profiles and external links. Shares the forum session; files download to Downloads.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebScreen(url: String, title: String, nav: Nav) {
    val context = LocalContext.current
    var progress by remember { mutableIntStateOf(0) }
    var pageTitle by remember { mutableStateOf(title) }
    var currentUrl by remember { mutableStateOf(url) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    BackHandler(enabled = canGoBack) { webView?.goBack() }

    ScreenScaffold(
        title = pageTitle.ifBlank { "Loading…" },
        subtitle = Uri.parse(currentUrl).host?.removePrefix("www.")?.let { if (it == "xiaomi.eu") "Xiaomi.EU" else it },
        onBack = { nav.back() },
        actions = {
            IconButton(onClick = { webView?.reload() }) { Icon(Icons.Outlined.Refresh, "Reload") }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Share link") },
                        leadingIcon = { Icon(Icons.Outlined.Share, null) },
                        onClick = { menu = false; shareLink(context, currentUrl, pageTitle) },
                    )
                    DropdownMenuItem(
                        text = { Text("Copy link") },
                        leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
                        onClick = { menu = false; copyLink(context, currentUrl) },
                    )
                }
            }
        },
    ) { mod ->
        Column(mod) {
            if (progress in 1..99) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        webView = this
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        settings.userAgentString = Http.userAgent
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        setDownloadListener { dlUrl, _, disposition, mime, _ ->
                            Downloads.start(ctx, dlUrl, disposition, mime)
                        }
                        webChromeClient = object : android.webkit.WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) { progress = newProgress }
                            override fun onReceivedTitle(view: WebView?, t: String?) {
                                if (t != null && (title.isBlank() || view?.canGoBack() == true)) {
                                    pageTitle = t.substringBefore(" | ")
                                }
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val u = request?.url ?: return false
                                val s = u.toString()
                                // Non-web schemes (mailto:, market:, intent:) can't open in a WebView.
                                if (u.scheme != "http" && u.scheme != "https") return true
                                // Threads and forums read better in the native screens.
                                if (isForumUrl(s) && (s.contains("/threads/") || s.contains("/forums/"))) {
                                    nav.link(s); return true
                                }
                                return false
                            }

                            override fun doUpdateVisitedHistory(view: WebView?, u: String?, isReload: Boolean) {
                                canGoBack = view?.canGoBack() == true
                                u?.let { currentUrl = it }
                            }

                            override fun onPageFinished(view: WebView?, u: String?) {
                                CookieManager.getInstance().flush()
                            }
                        }
                        loadUrl(url)
                    }
                },
                onRelease = { it.destroy(); webView = null },
            )
        }
    }
}

// ---------------------------------------------------------------- search

@Composable
fun SearchScreen(nav: Nav) {
    var query by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf("") }
    var titlesOnly by rememberSaveable { mutableStateOf(false) }
    var page by rememberSaveable { mutableIntStateOf(1) }
    var resultsUrl by rememberSaveable { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val loader = rememberLoader(submitted, titlesOnly, page) {
        if (submitted.isBlank()) null
        else ForumRepository.search(submitted, titlesOnly, if (page > 1) resultsUrl else null, page)
            .also { if (page == 1) resultsUrl = it.url }
    }

    fun submit() { if (query.isNotBlank()) { page = 1; resultsUrl = null; submitted = query.trim() } }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search Xiaomi.EU Forums") },
                        leadingIcon = { Icon(Icons.Outlined.Search, null, tint = MaterialTheme.colorScheme.primary) },
                        trailingIcon = {
                            if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, "Clear") }
                        },
                        singleLine = true,
                        shape = CircleShape,
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { submit() }),
                        modifier = Modifier.weight(1f).focusRequester(focus),
                    )
                }
                Row(Modifier.padding(start = 56.dp, top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(!titlesOnly, { titlesOnly = false; page = 1 }, { Text("Everything") })
                    FilterChip(titlesOnly, { titlesOnly = true; page = 1 }, { Text("Titles only") })
                }
            }
        }
        Box(Modifier.weight(1f)) {
            when {
                submitted.isBlank() -> EmptyState(
                    Icons.Outlined.Search, "Search the forum",
                    "Find ROM threads, bug reports and answers. Try a codename like \"houji\" or a version.",
                )
                else -> when (val s = loader.state) {
                    Load.Loading -> LoadingBox()
                    is Load.Err -> ErrorBox(s.message, loader.reload)
                    is Load.Ok -> SearchResults(s.value, nav) { page = it }
                }
            }
        }
    }
}

@Composable
private fun SearchResults(data: SearchPage?, nav: Nav, onPage: (Int) -> Unit) {
    if (data == null) return
    if (data.results.isEmpty()) {
        EmptyState(Icons.Outlined.SearchOff, "No results", "Try different words or search everything.")
        return
    }
    LazyColumn(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars)) {
        items(data.results, key = { it.url }) { r ->
            Surface(
                onClick = { nav.link(r.url) },
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(r.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (r.snippet.isNotBlank()) Text(
                        r.snippet, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        AvatarImage(r.avatarUrl, r.author, 20)
                        Text(
                            listOf(r.author, relativeTime(r.epoch)).filter { it.isNotBlank() }.joinToString(" · "),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 6.dp).weight(1f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        if (r.forum.isNotBlank()) Pill(r.forum, MaterialTheme.colorScheme.secondaryContainer,
                            MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
        }
        item { PagerBar(data.page, data.totalPages, onPage) }
        item { Box(Modifier.height(24.dp)) }
    }
}
