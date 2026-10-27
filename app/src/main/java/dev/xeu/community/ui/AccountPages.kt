package dev.xeu.community.ui

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.xeu.community.Nav
import dev.xeu.community.data.BASE
import dev.xeu.community.data.ForumRepository
import dev.xeu.community.data.Http
import kotlinx.coroutines.launch

@Composable
private fun NeedsLogin(nav: Nav, modifier: Modifier) {
    ErrorBox("Log in to see this.", onRetry = { nav.login() }, modifier = modifier)
}

@Composable
fun AlertsScreen(nav: Nav) {
    val scope = rememberCoroutineScope()
    val loader = rememberLoader("alerts") { ForumRepository.alerts() }
    ScreenScaffold(
        title = "Alerts",
        onBack = { nav.back() },
        actions = {
            IconButton(onClick = { scope.launch { runCatching { ForumRepository.markAlertsRead() }; loader.reload() } }) {
                Icon(Icons.Filled.DoneAll, "Mark all read")
            }
            IconButton(onClick = loader.reload) { Icon(Icons.Filled.Refresh, "Refresh") }
        },
    ) { mod ->
        if (!Http.isLoggedIn()) { NeedsLogin(nav, mod); return@ScreenScaffold }
        when (val s = loader.state) {
            Load.Loading -> LoadingBox(mod)
            is Load.Err -> ErrorBox(s.message, loader.reload, mod)
            is Load.Ok -> LazyColumn(mod) {
                if (s.value.isEmpty()) item { Text("No alerts yet.", Modifier.padding(24.dp)) }
                items(s.value, key = { it.id }) { a ->
                    ListItem(
                        headlineContent = {
                            Text(a.text, fontWeight = if (a.unread) FontWeight.SemiBold else FontWeight.Normal, maxLines = 4,
                                overflow = TextOverflow.Ellipsis)
                        },
                        supportingContent = { Text(relativeTime(a.epoch)) },
                        leadingContent = { AvatarImage(a.avatarUrl, a.text, 40) },
                        trailingContent = {
                            if (a.unread) Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                        },
                        colors = if (a.unread) ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ) else ListItemDefaults.colors(),
                        modifier = Modifier.clickable(enabled = a.link != null) { a.link?.let(nav::link) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun ConversationsScreen(nav: Nav) {
    val context = LocalContext.current
    val loader = rememberLoader("convos") { ForumRepository.conversations() }
    ScreenScaffold(
        title = "Direct messages",
        onBack = { nav.back() },
        actions = {
            IconButton(onClick = { nav.web(BASE + "conversations/add", "New message") }) {
                Icon(Icons.Filled.Edit, "New message")
            }
            IconButton(onClick = loader.reload) { Icon(Icons.Filled.Refresh, "Refresh") }
        },
    ) { mod ->
        if (!Http.isLoggedIn()) { NeedsLogin(nav, mod); return@ScreenScaffold }
        when (val s = loader.state) {
            Load.Loading -> LoadingBox(mod)
            is Load.Err -> ErrorBox(s.message, loader.reload, mod)
            is Load.Ok -> LazyColumn(mod) {
                if (s.value.isEmpty()) item { Text("No conversations yet.", Modifier.padding(24.dp)) }
                items(s.value, key = { it.id }) { c ->
                    ListItem(
                        headlineContent = {
                            Text(c.title, fontWeight = if (c.unread) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                        },
                        supportingContent = {
                            Text(
                                listOf(c.participants.joinToString(), relativeTime(c.lastEpoch))
                                    .filter { it.isNotBlank() }.joinToString(" · "),
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        },
                        leadingContent = { AvatarImage(null, c.lastSender.ifBlank { c.title }, 40) },
                        trailingContent = {
                            if (c.unread) Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                        },
                        modifier = Modifier.clickable { nav.web(c.url, c.title) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun WatchedScreen(nav: Nav) {
    var page by rememberSaveable { mutableIntStateOf(1) }
    val loader = rememberLoader("watched", page) { ForumRepository.watchedThreads(page) }
    ScreenScaffold(
        title = "Watched threads",
        onBack = { nav.back() },
        actions = { IconButton(onClick = loader.reload) { Icon(Icons.Filled.Refresh, "Refresh") } },
    ) { mod ->
        if (!Http.isLoggedIn()) { NeedsLogin(nav, mod); return@ScreenScaffold }
        when (val s = loader.state) {
            Load.Loading -> LoadingBox(mod)
            is Load.Err -> ErrorBox(s.message, loader.reload, mod)
            is Load.Ok -> LazyColumn(mod) {
                val f = s.value
                if (f.threads.isEmpty()) item {
                    Text("You aren't watching any threads. Watch one on the website to follow its replies.",
                        Modifier.padding(24.dp))
                }
                item { PagerBar(f.page, f.totalPages) { page = it } }
                items(f.threads, key = { it.id }) { t ->
                    ThreadRow(t, onOpen = { nav.thread(t.url, 1) }, onLatest = {
                        nav.thread(ForumRepository.baseUrl(t.url) + "unread", 0)
                    })
                }
                item { PagerBar(f.page, f.totalPages) { page = it } }
            }
        }
    }
}

/**
 * Signed-in forum pages the app doesn't render natively (account settings, conversations,
 * profiles). Shares the app's cookies, stays on xiaomi.eu, and opens threads natively.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebScreen(url: String, title: String, nav: Nav) {
    val context = LocalContext.current
    var progress by remember { mutableIntStateOf(0) }
    var pageTitle by remember { mutableStateOf(title) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }

    BackHandler(enabled = canGoBack) { webView?.goBack() }

    ScreenScaffold(
        title = pageTitle.ifBlank { "xiaomi.eu" },
        onBack = { nav.back() },
        actions = {
            IconButton(onClick = { webView?.reload() }) { Icon(Icons.Filled.Refresh, "Reload") }
            IconButton(onClick = { openExternal(context, webView?.url ?: url) }) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, "Open in browser")
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
                        settings.userAgentString = Http.userAgent
                        CookieManager.getInstance().setAcceptCookie(true)
                        webChromeClient = object : android.webkit.WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) { progress = newProgress }
                            override fun onReceivedTitle(view: WebView?, t: String?) {
                                if (title.isBlank() && t != null) pageTitle = t.substringBefore(" | ")
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val u = request?.url?.toString() ?: return false
                                if (!isForumUrl(u)) { openExternal(ctx, u); return true }
                                // Threads and forums read better in the native screens.
                                if (u.contains("/threads/") || u.contains("/forums/")) { nav.link(u); return true }
                                return false
                            }

                            override fun doUpdateVisitedHistory(view: WebView?, u: String?, isReload: Boolean) {
                                canGoBack = view?.canGoBack() == true
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
