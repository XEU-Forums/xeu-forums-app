package dev.xeu.reader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.xeu.reader.ui.AccountScreen
import dev.xeu.reader.ui.ComposeScreen
import dev.xeu.reader.ui.DevicePickerScreen
import dev.xeu.reader.ui.DevicesScreen
import dev.xeu.reader.ui.ForumScreen
import dev.xeu.reader.ui.ForumsScreen
import dev.xeu.reader.ui.LoginScreen
import dev.xeu.reader.ui.RomsScreen
import dev.xeu.reader.ui.ThreadScreen
import dev.xeu.reader.ui.XeuTheme
import dev.xeu.reader.ui.isForumUrl
import dev.xeu.reader.ui.openExternal

class MainActivity : ComponentActivity() {
    private var pendingLink by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        pendingLink = intent?.data?.toString()
        setContent { XeuTheme { App(pendingLink) { pendingLink = null } } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.toString()?.let { pendingLink = it }
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("forums", "Forums", Icons.Filled.Forum),
    Tab("devices", "My devices", Icons.Filled.PhoneAndroid),
    Tab("roms", "ROMs", Icons.Filled.SystemUpdate),
    Tab("account", "Account", Icons.Filled.AccountCircle),
)

/** Navigation helpers shared by every screen. */
class Nav(private val nav: NavHostController, private val context: android.content.Context) {
    fun forum(url: String) = nav.navigate("forum?url=${Uri.encode(url)}")
    fun thread(url: String, page: Int = 0) = nav.navigate("thread?url=${Uri.encode(url)}&page=$page")
    fun reply(threadUrl: String, quote: String = "") =
        nav.navigate("compose?mode=reply&url=${Uri.encode(threadUrl)}&quote=${Uri.encode(quote)}")
    fun newThread(forumUrl: String) = nav.navigate("compose?mode=thread&url=${Uri.encode(forumUrl)}&quote=")
    fun login() = nav.navigate("login")
    fun devicePicker() = nav.navigate("devicePicker")
    fun back() = nav.popBackStack()

    /** After posting: leave the composer (and the stale thread view for replies), then show the result. */
    fun afterPost(resultUrl: String, replacingThread: Boolean) {
        nav.popBackStack()
        if (replacingThread && nav.currentBackStackEntry?.destination?.route?.startsWith("thread") == true) {
            nav.popBackStack()
        }
        link(resultUrl)
    }

    /** Opens forum links in the app, everything else in the browser. */
    fun link(url: String) {
        when {
            !isForumUrl(url) -> openExternal(context, url)
            url.contains("/link-forums/") -> openExternal(context, url)
            url.contains("/threads/") || url.contains("/goto/post") || url.contains("/posts/") -> thread(url, 0)
            url.contains("/forums/") || url.contains("/categories/") -> forum(url)
            url.contains("/login") -> login()
            else -> openExternal(context, url)
        }
    }
}

@Composable
private fun App(pendingLink: String?, consumeLink: () -> Unit) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val n = Nav(nav, context)
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    LaunchedEffect(pendingLink) {
        pendingLink?.let { if (isForumUrl(it)) n.link(it) else openExternal(context, it); consumeLink() }
    }

    Scaffold(
        bottomBar = {
            if (tabs.any { it.route == route }) {
                NavigationBar {
                    tabs.forEach { t ->
                        NavigationBarItem(
                            selected = route == t.route,
                            onClick = {
                                nav.navigate(t.route) {
                                    popUpTo("forums") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(t.icon, null) },
                            label = { Text(t.label) },
                        )
                    }
                }
            }
        },
    ) { pad ->
        NavHost(nav, startDestination = "forums", modifier = Modifier.padding(bottom = pad.calculateBottomPadding())) {
            composable("forums") { ForumsScreen(n) }
            composable("devices") { DevicesScreen(n) }
            composable("devicePicker") { DevicePickerScreen(n) }
            composable("roms") { RomsScreen(n) }
            composable("account") { AccountScreen(n) }
            composable("login") { LoginScreen(n) }
            composable(
                "forum?url={url}",
                arguments = listOf(navArgument("url") { type = NavType.StringType }),
            ) { e -> ForumScreen(e.arguments?.getString("url").orEmpty(), n) }
            composable(
                "thread?url={url}&page={page}",
                arguments = listOf(
                    navArgument("url") { type = NavType.StringType },
                    navArgument("page") { type = NavType.IntType; defaultValue = 0 },
                ),
            ) { e -> ThreadScreen(e.arguments?.getString("url").orEmpty(), e.arguments?.getInt("page") ?: 0, n) }
            composable(
                "compose?mode={mode}&url={url}&quote={quote}",
                arguments = listOf(
                    navArgument("mode") { type = NavType.StringType },
                    navArgument("url") { type = NavType.StringType },
                    navArgument("quote") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { e ->
                ComposeScreen(
                    mode = e.arguments?.getString("mode").orEmpty(),
                    targetUrl = e.arguments?.getString("url").orEmpty(),
                    initialQuote = e.arguments?.getString("quote").orEmpty(),
                    nav = n,
                )
            }
        }
    }
}
