package dev.xeu.community.ui

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Bookmarks
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
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import dev.xeu.community.Nav
import dev.xeu.community.data.Accounts
import dev.xeu.community.data.BASE
import dev.xeu.community.data.ForumRepository
import dev.xeu.community.data.Http
import dev.xeu.community.data.Me
import dev.xeu.community.data.Profile
import dev.xeu.community.data.SavedAccount
import dev.xeu.community.work.Scheduler
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

    fun switchTo(id: Int) = scope.launch {
        busy = true
        accounts.switchTo(id)
        busy = false
        version++
        Scheduler.checkForumNow(context.applicationContext)
    }

    ScreenScaffold(
        title = "Account",
        actions = { IconButton(onClick = { nav.settings() }) { Icon(Icons.Outlined.Settings, "Settings") } },
    ) { mod ->
        Column(mod.verticalScroll(rememberScrollState())) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            when (val s = loader.state) {
                Load.Loading -> LoadingBox(Modifier.height(240.dp))
                is Load.Err -> ErrorBox(s.message, loader.reload, Modifier.height(240.dp))
                is Load.Ok -> {
                    val me = s.value.me
                    if (me == null) LoggedOut(nav) else {
                        ProfileCard(me, s.value.profile)
                        Section("Activity")
                        NavRow(Icons.Outlined.Notifications, "Alerts", me.unreadAlerts) { nav.alerts() }
                        NavRow(Icons.Outlined.Mail, "Direct messages", me.unreadConversations) { nav.conversations() }
                        NavRow(Icons.Outlined.Visibility, "Watched threads") { nav.watched() }
                        NavRow(Icons.Outlined.Bookmarks, "Bookmarks") { nav.web(BASE + "account/bookmarks", "Bookmarks") }
                        NavRow(Icons.Outlined.AccountCircle, "Your profile page") {
                            nav.web(BASE + "members/${me.userId}/", me.username)
                        }

                        Section("Account settings")
                        NavRow(Icons.Outlined.Edit, "Account details") { nav.web(BASE + "account/account-details", "Account details") }
                        NavRow(Icons.Outlined.Lock, "Password and security") { nav.web(BASE + "account/security", "Password and security") }
                        NavRow(Icons.Outlined.PrivacyTip, "Privacy") { nav.web(BASE + "account/privacy", "Privacy") }
                        NavRow(Icons.Outlined.Tune, "Forum preferences") { nav.web(BASE + "account/preferences", "Preferences") }
                        NavRow(Icons.Outlined.Edit, "Signature") { nav.web(BASE + "account/signature", "Signature") }
                    }
                }
            }

            Section("Accounts on this device")
            saved.forEach { acc ->
                AccountRow(acc, active = acc.userId == activeId && Http.isLoggedIn()) {
                    if (acc.userId != activeId || !Http.isLoggedIn()) switchTo(acc.userId)
                }
            }
            ListItem(
                headlineContent = { Text("Add another account") },
                leadingContent = { Icon(Icons.Outlined.PersonAdd, null) },
                modifier = Modifier.clickable {
                    scope.launch { accounts.startAddAccount(); nav.login() }
                },
            )
            if (activeId != null && Http.isLoggedIn()) {
                ListItem(
                    headlineContent = { Text("Log out", color = MaterialTheme.colorScheme.error) },
                    leadingContent = {
                        Icon(Icons.AutoMirrored.Outlined.Logout, null, tint = MaterialTheme.colorScheme.error)
                    },
                    modifier = Modifier.clickable { confirmLogout = true },
                )
            }
            Text(
                "Passwords are typed on the forum's own login page and never stored. Each account's session is " +
                    "kept encrypted on this device so you can switch without logging in again.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }

    if (confirmLogout) {
        val name = saved.firstOrNull { it.userId == activeId }?.username ?: "this account"
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
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
private fun LoggedOut(nav: Nav) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Outlined.AccountCircle, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
        Text("Not logged in", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 8.dp))
        Text(
            "You can read everything without an account. Log in to reply, get alerts and use direct messages.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        Button(onClick = { nav.login() }) { Text("Log in to xiaomi.eu") }
    }
}

@Composable
private fun ProfileCard(me: Me, profile: Profile?) {
    ElevatedCard(Modifier.fillMaxWidth().padding(12.dp)) {
        Box {
            Box(
                Modifier.fillMaxWidth().height(72.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer)
            ) {
                profile?.bannerUrl?.let {
                    AsyncImage(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
            }
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 40.dp, bottom = 16.dp)) {
                AvatarImage(profile?.avatarUrl ?: me.avatarUrl, me.username, 64)
                Text(
                    me.username,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp),
                )
                profile?.userTitle?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val stats = profile?.stats.orEmpty()
                if (stats.isNotEmpty()) {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        stats.take(6).forEach { (k, v) -> SuggestionChip(onClick = {}, label = { Text("$k: $v") }) }
                    }
                }
            }
        }
    }
}

@Composable
fun AvatarImage(url: String?, name: String, sizeDp: Int) {
    val mod = Modifier.size(sizeDp.dp).clip(CircleShape)
    if (url != null) {
        AsyncImage(url, null, mod.background(MaterialTheme.colorScheme.surface), contentScale = ContentScale.Crop)
    } else {
        Box(mod.background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
            Text(
                name.take(1).uppercase(),
                style = if (sizeDp >= 48) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun NavRow(icon: ImageVector, title: String, badge: Int = 0, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        leadingContent = { Icon(icon, null) },
        trailingContent = { if (badge > 0) Badge { Text(badge.coerceAtMost(99).toString()) } },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun AccountRow(acc: SavedAccount, active: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(acc.username) },
        supportingContent = { Text(if (active) "Active" else "Tap to switch") },
        leadingContent = { AvatarImage(acc.avatarUrl, acc.username, 40) },
        trailingContent = {
            if (active) Icon(Icons.Filled.CheckCircle, "Active", tint = MaterialTheme.colorScheme.primary)
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(nav: Nav) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var progress by remember { mutableIntStateOf(0) }
    var done by remember { mutableStateOf(false) }

    ScreenScaffold(title = "Log in", onBack = { nav.back() }) { mod ->
        Column(mod) {
            if (progress in 1..99 || done) LinearProgressIndicator(
                progress = { if (done) 1f else progress / 100f }, modifier = Modifier.fillMaxWidth()
            )
            Text(
                "Use your forum username/email and password, and tick \"Stay logged in\". " +
                    "Google sign-in won't work inside apps.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.userAgentString = Http.userAgent
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                        webChromeClient = object : android.webkit.WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) { progress = newProgress }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val host = request?.url?.host.orEmpty()
                                // Keep login on xiaomi.eu; anything else opens in the browser.
                                if (!host.endsWith("xiaomi.eu")) {
                                    openExternal(ctx, request?.url.toString()); return true
                                }
                                return false
                            }

                            override fun onPageFinished(view: WebView?, url: String?) { check() }

                            // XenForo marks the page with data-logged-in="true" once the session is signed in.
                            private fun check() {
                                if (done) return
                                evaluateJavascript("document.documentElement.getAttribute('data-logged-in')") { v ->
                                    if (!done && v?.trim('"') == "true") {
                                        done = true
                                        CookieManager.getInstance().flush()
                                        Http.loggedIn.value = true
                                        scope.launch {
                                            runCatching {
                                                ForumRepository.me()?.let { Accounts(context.applicationContext).saveCurrent(it) }
                                            }
                                            Scheduler.checkForumNow(context.applicationContext)
                                            nav.back()
                                        }
                                    }
                                }
                            }
                        }
                        loadUrl(BASE + "login/")
                    }
                },
            )
        }
    }
}
