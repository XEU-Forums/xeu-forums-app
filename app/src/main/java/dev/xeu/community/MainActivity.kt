package dev.xeu.community

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import dev.xeu.community.data.AppSettings
import dev.xeu.community.data.Http
import dev.xeu.community.data.SettingsStore
import dev.xeu.community.ui.AccountScreen
import dev.xeu.community.ui.AlertsScreen
import dev.xeu.community.ui.ComposeScreen
import dev.xeu.community.ui.ConversationsScreen
import dev.xeu.community.ui.DevicePickerScreen
import dev.xeu.community.ui.DevicesScreen
import dev.xeu.community.ui.ForumScreen
import dev.xeu.community.ui.ForumsScreen
import dev.xeu.community.ui.LoginScreen
import dev.xeu.community.ui.RomsScreen
import dev.xeu.community.ui.SettingsScreen
import dev.xeu.community.ui.StartupUpdatePrompt
import dev.xeu.community.ui.ThreadScreen
import dev.xeu.community.ui.WatchedScreen
import dev.xeu.community.ui.WebScreen
import dev.xeu.community.ui.XeuTheme
import dev.xeu.community.ui.isForumUrl
import dev.xeu.community.ui.openExternal
import dev.xeu.community.work.Notifier

/** Something to open once the UI is ready: a link, or a named screen from a notification. */
data class Pending(val link: String?, val open: String?)

class MainActivity : ComponentActivity() {
    private var pending by mutableStateOf<Pending?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) pending = intent.toPending()
        val store = SettingsStore(applicationContext)
        setContent {
            val settings by store.settings.collectAsState(initial = AppSettings())
            XeuTheme(settings) { App(pending) { pending = null } }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.toPending()?.let { pending = it }
    }

    private fun Intent.toPending(): Pending? {
        val link = data?.toString()
        val open = getStringExtra(Notifier.EXTRA_OPEN)
        return if (link == null && open == null) null else Pending(link, open)
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabs = listOf(
    Tab("forums", "Forums", Icons.Outlined.Forum, Icons.Filled.Forum),
    Tab("devices", "My devices", Icons.Outlined.PhoneAndroid, Icons.Filled.PhoneAndroid),
    Tab("roms", "ROMs", Icons.Outlined.SystemUpdate, Icons.Filled.SystemUpdate),
    Tab("account", "Account", Icons.Outlined.AccountCircle, Icons.Filled.AccountCircle),
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
    fun settings() = nav.navigate("settings")
    fun alerts() = nav.navigate("alerts")
    fun conversations() = nav.navigate("conversations")
    fun watched() = nav.navigate("watched")
    fun web(url: String, title: String = "") =
        nav.navigate("web?url=${Uri.encode(url)}&title=${Uri.encode(title)}")
    fun tab(route: String) = nav.navigate(route) {
        popUpTo("forums") { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
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
            url.contains("/account/alerts") -> alerts()
            url.contains("/watched/threads") -> watched()
            url.contains("/conversations/") || url.contains("/account/") || url.contains("/members/") ||
                url.contains("/profile-posts/") -> web(url)
            else -> openExternal(context, url)
        }
    }
}

@Composable
private fun App(pending: Pending?, consumePending: () -> Unit) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val n = remember(nav) { Nav(nav, context) }
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    var showUpdate by remember { mutableStateOf(false) }

    // Ask once for notification permission on Android 13+.
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !Notifier.allowed(context)) {
            val prefs = context.getSharedPreferences("ui", 0)
            if (!prefs.getBoolean("asked_notifications", false)) {
                prefs.edit().putBoolean("asked_notifications", true).apply()
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(pending) {
        val p = pending ?: return@LaunchedEffect
        when (p.open) {
            Notifier.OPEN_UPDATES -> showUpdate = true
            "alerts" -> n.alerts()
            "roms" -> n.tab("roms")
        }
        p.link?.let { if (isForumUrl(it)) n.link(it) else openExternal(context, it) }
        consumePending()
    }

    StartupUpdatePrompt(forceShow = showUpdate, onClosed = { showUpdate = false })

    val unread = Http.unreadAlerts.value + Http.unreadConversations.value

    Scaffold(
        bottomBar = {
            if (tabs.any { it.route == route }) {
                NavigationBar {
                    tabs.forEach { t ->
                        val selected = route == t.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = { n.tab(t.route) },
                            icon = {
                                BadgedBox(badge = {
                                    if (t.route == "account" && unread > 0) Badge { Text(unread.coerceAtMost(99).toString()) }
                                }) { Icon(if (selected) t.selectedIcon else t.icon, null) }
                            },
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
            composable("settings") { SettingsScreen(n) }
            composable("alerts") { AlertsScreen(n) }
            composable("conversations") { ConversationsScreen(n) }
            composable("watched") { WatchedScreen(n) }
            composable(
                "web?url={url}&title={title}",
                arguments = listOf(
                    navArgument("url") { type = NavType.StringType },
                    navArgument("title") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { e -> WebScreen(e.arguments?.getString("url").orEmpty(), e.arguments?.getString("title").orEmpty(), n) }
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
