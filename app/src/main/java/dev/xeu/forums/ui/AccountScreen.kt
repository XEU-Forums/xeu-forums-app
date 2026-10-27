package dev.xeu.forums.ui

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import dev.xeu.forums.Nav
import dev.xeu.forums.data.Accounts
import dev.xeu.forums.data.BASE
import dev.xeu.forums.data.ForumRepository
import dev.xeu.forums.data.Http
import dev.xeu.forums.data.Me
import dev.xeu.forums.data.Profile
import dev.xeu.forums.work.Scheduler
import kotlinx.coroutines.launch

private data class AccountData(val me: Me?, val profile: Profile?)

@Composable
fun AccountScreen(nav: Nav) {
    val context = LocalContext.current
    val accounts = remember { Accounts(context.applicationContext) }
    val saved by accounts.accounts.collectAsState(initial = emptyList())
    val activeId by accounts.activeId.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var version by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }

    val loader = rememberLoader(version) {
        val me = ForumRepository.me()
        if (me != null) runCatching { accounts.saveCurrent(me) }
        AccountData(me, me?.let { runCatching { ForumRepository.profile(it.userId) }.getOrNull() })
    }
    val me = (loader.state as? Load.Ok)?.value?.me
    val profile = (loader.state as? Load.Ok)?.value?.profile

    fun switchTo(id: Int) = scope.launch {
        busy = true
        accounts.switchTo(id)
        busy = false
        version++
        Scheduler.checkForumNow(context.applicationContext)
    }

    Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0)) { pad ->
        RefreshBox(loader.refreshing, loader.reload, Modifier.padding(pad).fillMaxSize()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                ProfileHero(me, profile, loading = loader.state is Load.Loading, onSettings = { nav.settings() }, onLogin = { nav.login() })
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp))
                (loader.state as? Load.Err)?.let { ErrorBox(it.message, loader.reload, Modifier.height(260.dp)) }

                if (me != null) {
                    SectionLabel("Activity")
                    SectionCard {
                        CardRow("Alerts", "Replies, quotes and reactions", Icons.Outlined.Notifications,
                            trailing = { CountOrChevron(me.unreadAlerts) }) { nav.alerts() }
                        CardDivider()
                        CardRow("Direct messages", "Private conversations", Icons.Outlined.Mail,
                            trailing = { CountOrChevron(me.unreadConversations) }) { nav.conversations() }
                        CardDivider()
                        CardRow("Watched threads", "Threads you follow", Icons.Outlined.Visibility) { nav.watched() }
                        CardDivider()
                        CardRow("Bookmarks", "Saved posts", Icons.Outlined.Bookmarks) {
                            nav.web(BASE + "account/bookmarks", "Bookmarks")
                        }
                        CardDivider()
                        CardRow("Profile page", "How others see you", Icons.Outlined.AccountCircle) {
                            nav.web(BASE + "members/${me.userId}/", me.username)
                        }
                    }

                    SectionLabel("Account settings")
                    SectionCard {
                        CardRow("Account details", "Email, avatar, about you", Icons.Outlined.Edit) {
                            nav.web(BASE + "account/account-details", "Account details")
                        }
                        CardDivider()
                        CardRow("Password and security", "Password, two-step verification", Icons.Outlined.Lock) {
                            nav.web(BASE + "account/security", "Password and security")
                        }
                        CardDivider()
                        CardRow("Privacy", "Who can see and contact you", Icons.Outlined.PrivacyTip) {
                            nav.web(BASE + "account/privacy", "Privacy")
                        }
                        CardDivider()
                        CardRow("Forum preferences", "Email and alert options on the forum", Icons.Outlined.Tune) {
                            nav.web(BASE + "account/preferences", "Preferences")
                        }
                        CardDivider()
                        CardRow("Signature", "Shown under your posts", Icons.Outlined.Draw) {
                            nav.web(BASE + "account/signature", "Signature")
                        }
                    }
                }

                SectionLabel("Accounts on this device")
                SectionCard {
                    saved.forEachIndexed { i, acc ->
                        if (i > 0) CardDivider()
                        val active = acc.userId == activeId && Http.isLoggedIn()
                        CardRow(
                            acc.username,
                            if (active) "Signed in" else "Tap to switch",
                            leading = { AvatarImage(acc.avatarUrl, acc.username, 40) },
                            trailing = {
                                if (active) Icon(Icons.Filled.CheckCircle, "Active", tint = MaterialTheme.colorScheme.primary)
                            },
                        ) { if (!active) switchTo(acc.userId) }
                    }
                    if (saved.isNotEmpty()) CardDivider()
                    CardRow("Add account", null, Icons.Outlined.PersonAdd, trailing = {}) {
                        scope.launch { accounts.startAddAccount(); nav.login() }
                    }
                    if (activeId != null && Http.isLoggedIn()) {
                        CardDivider()
                        CardRow(
                            "Log out", null,
                            leading = {
                                IconTile(Icons.AutoMirrored.Outlined.Logout,
                                    container = MaterialTheme.colorScheme.errorContainer,
                                    tint = MaterialTheme.colorScheme.onErrorContainer)
                            },
                            titleColor = MaterialTheme.colorScheme.error,
                            trailing = {},
                        ) { confirmLogout = true }
                    }
                }
                Text(
                    "Passwords are typed on the forum's own login page and never stored. Each account's session " +
                        "is kept encrypted on this device so you can switch without logging in again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(20.dp),
                )
            }
        }
    }

    if (confirmLogout) {
        val name = saved.firstOrNull { it.userId == activeId }?.username ?: "this account"
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            icon = { Icon(Icons.AutoMirrored.Outlined.Logout, null) },
            title = { Text("Log out of $name?") },
            text = { Text("You'll be signed out on the forum and the account is removed from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = false
                    val id = activeId ?: return@TextButton
                    scope.launch { busy = true; accounts.logout(id); busy = false; version++ }
                }) { Text("Log out") }
            },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun CountOrChevron(count: Int) {
    if (count > 0) Badge(containerColor = MaterialTheme.colorScheme.primary) {
        Text(count.coerceAtMost(99).toString(), modifier = Modifier.padding(horizontal = 2.dp))
    } else Icon(
        Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
        tint = MaterialTheme.colorScheme.outline,
    )
}

@Composable
private fun ProfileHero(me: Me?, profile: Profile?, loading: Boolean, onSettings: () -> Unit, onLogin: () -> Unit) {
    val accents = LocalAccents.current
    Box {
        HeroHeader {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Account", style = MaterialTheme.typography.titleLarge, color = accents.onHero, modifier = Modifier.weight(1f))
                IconButton(
                    onClick = onSettings,
                    colors = IconButtonDefaults.iconButtonColors(containerColor = Color.White.copy(alpha = 0.18f)),
                ) { Icon(Icons.Outlined.Settings, "Settings", tint = accents.onHero) }
            }
            when {
                me != null -> {
                    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.border(3.dp, Color.White.copy(alpha = 0.85f), CircleShape).padding(3.dp)) {
                            AvatarImage(profile?.avatarUrl ?: me.avatarUrl, me.username, 72)
                        }
                        Column(Modifier.padding(start = 16.dp)) {
                            Text(me.username, style = MaterialTheme.typography.headlineSmall, color = accents.onHero,
                                fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            profile?.userTitle?.takeIf { it.isNotBlank() }?.let {
                                Text(it, style = MaterialTheme.typography.bodyMedium, color = accents.onHero.copy(alpha = 0.85f))
                            }
                        }
                    }
                    val stats = profile?.stats.orEmpty().take(4)
                    if (stats.isNotEmpty()) {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 16.dp).horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            stats.forEach { (k, v) ->
                                Column(
                                    Modifier.clip(MaterialTheme.shapes.medium)
                                        .background(Color.White.copy(alpha = 0.16f))
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Text(v, style = MaterialTheme.typography.titleMedium, color = accents.onHero,
                                        fontWeight = FontWeight.Bold, maxLines = 1)
                                    Text(k, style = MaterialTheme.typography.labelSmall, color = accents.onHero.copy(alpha = 0.85f))
                                }
                            }
                        }
                    }
                }
                loading -> Box(Modifier.height(96.dp))
                else -> {
                    Text(
                        "Welcome",
                        style = MaterialTheme.typography.headlineMedium,
                        color = accents.onHero,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Text(
                        "Read everything as a guest. Log in to reply, get alerts and send direct messages.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = accents.onHero.copy(alpha = 0.9f),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Button(
                        onClick = onLogin,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Brand.OrangeDeep),
                        modifier = Modifier.padding(top = 16.dp),
                    ) { Text("Log in to Xiaomi.EU") }
                }
            }
        }
    }
}

@Composable
fun AvatarImage(url: String?, name: String, sizeDp: Int) {
    val mod = Modifier.size(sizeDp.dp).clip(CircleShape)
    if (url != null) {
        AsyncImage(url, null, mod.background(MaterialTheme.colorScheme.surfaceContainerHighest), contentScale = ContentScale.Crop)
    } else {
        Box(mod.background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Text(
                name.trim().take(1).uppercase(),
                style = when {
                    sizeDp >= 56 -> MaterialTheme.typography.headlineMedium
                    sizeDp >= 36 -> MaterialTheme.typography.titleMedium
                    else -> MaterialTheme.typography.labelSmall
                },
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(nav: Nav) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var progress by remember { mutableIntStateOf(0) }
    var done by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    fun finish() {
        if (done) return
        done = true
        Http.unifyForumCookies()
        Http.loggedIn.value = true
        scope.launch {
            runCatching { ForumRepository.me()?.let { Accounts(context.applicationContext).saveCurrent(it) } }
            Scheduler.checkForumNow(context.applicationContext)
            nav.back()
        }
    }

    // XenForo marks the page with data-logged-in="true" once signed in. Check on every page event
    // and also poll, so the screen closes as soon as the forum has accepted the login.
    fun check() {
        if (done) return
        webView?.evaluateJavascript("document.documentElement.getAttribute('data-logged-in')") { v ->
            if (v?.trim('"') == "true") finish()
        }
    }
    androidx.compose.runtime.LaunchedEffect(webView) {
        while (!done && webView != null) {
            check()
            kotlinx.coroutines.delay(500)
        }
    }

    ScreenScaffold(title = "Log in", subtitle = "Xiaomi.EU Forums", onBack = { nav.back() }) { mod ->
        Column(mod) {
            if (progress in 1..99 || done) LinearProgressIndicator(
                progress = { if (done) 1f else progress / 100f }, modifier = Modifier.fillMaxWidth()
            )
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Lock, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(10.dp))
                Text(
                    if (done) "Signed in. Returning to the app…"
                    else "Sign in with your forum username or email and tick \"Stay logged in\". " +
                        "Google sign-in isn't supported inside apps.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        webView = this
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.userAgentString = Http.userAgent
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webChromeClient = object : android.webkit.WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) { progress = newProgress }
                        }
                        webViewClient = object : WebViewClient() {
                            // Everything (captcha, two-step, password reset) stays inside this screen.
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val scheme = request?.url?.scheme
                                return scheme != "http" && scheme != "https"
                            }
                            override fun onPageCommitVisible(view: WebView?, url: String?) { check() }
                            override fun onPageFinished(view: WebView?, url: String?) { check() }
                        }
                        loadUrl(BASE + "login/")
                    }
                },
                onRelease = { it.destroy(); webView = null },
            )
        }
    }
}
