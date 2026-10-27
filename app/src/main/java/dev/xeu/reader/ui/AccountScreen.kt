package dev.xeu.reader.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.xeu.reader.Nav
import dev.xeu.reader.data.BASE
import dev.xeu.reader.data.ForumRepository
import dev.xeu.reader.data.Http

@Composable
fun AccountScreen(nav: Nav) {
    val context = LocalContext.current
    val loggedIn = Http.isLoggedIn()
    // Loading the index refreshes the login state and gives us the username.
    val session = rememberLoader(loggedIn) { if (loggedIn) ForumRepository.session() else null }

    ScreenScaffold(title = "Account") { mod ->
        Column(mod.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (loggedIn) {
                val name = (session.state as? Load.Ok)?.value?.username
                Text(
                    if (name != null) "Logged in as $name" else "Logged in",
                    style = MaterialTheme.typography.titleLarge,
                )
                if ((session.state as? Load.Ok)?.value?.loggedIn == false) {
                    Text("Your forum session has expired. Log in again.", color = MaterialTheme.colorScheme.error)
                }
                OutlinedButton(onClick = { openExternal(context, BASE + "account/") }) { Text("Account settings (browser)") }
                OutlinedButton(onClick = { Http.logout() }) { Text("Log out") }
            } else {
                Text("Not logged in", style = MaterialTheme.typography.titleLarge)
                Text(
                    "You can read everything without an account. Log in to reply, quote and start threads.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = { nav.login() }) { Text("Log in to xiaomi.eu") }
            }
            Text(
                "XEU Reader is an unofficial client. Your password is entered on the forum's own login page; " +
                    "the app only keeps the session cookie the forum sets.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 24.dp),
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(nav: Nav) {
    var progress by remember { mutableIntStateOf(0) }
    var done by remember { mutableStateOf(false) }

    ScreenScaffold(title = "Log in", onBack = { nav.back() }) { mod ->
        Column(mod) {
            if (progress in 1..99) LinearProgressIndicator(
                progress = { progress / 100f }, modifier = Modifier.fillMaxWidth()
            )
            Text(
                "Use your forum username/email and password. Google sign-in won't work inside apps.",
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
                                evaluateJavascript(
                                    "document.documentElement.getAttribute('data-logged-in')"
                                ) { v ->
                                    if (!done && v?.trim('"') == "true") {
                                        done = true
                                        CookieManager.getInstance().flush()
                                        Http.loggedIn.value = true
                                        nav.back()
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
