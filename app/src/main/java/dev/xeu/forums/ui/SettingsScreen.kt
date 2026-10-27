package dev.xeu.forums.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.xeu.forums.BuildConfig
import dev.xeu.forums.Nav
import dev.xeu.forums.data.AppSettings
import dev.xeu.forums.data.SettingsStore
import dev.xeu.forums.data.ThemeMode
import dev.xeu.forums.update.Updater
import dev.xeu.forums.work.Notifier
import dev.xeu.forums.work.Scheduler
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(nav: Nav) {
    val context = LocalContext.current
    val store = remember { SettingsStore(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val s by store.settings.collectAsState(initial = AppSettings())

    fun change(reschedule: Boolean = false, f: (AppSettings) -> AppSettings) = scope.launch {
        store.update(f)
        if (reschedule) Scheduler.applyAll(context.applicationContext)
    }

    ScreenScaffold(title = "Settings", large = true, onBack = { nav.back() }) { mod ->
        Column(mod.verticalScroll(rememberScrollState())) {
            SectionLabel("Appearance")
            SectionCard {
                CardRow("Theme", "Follow the system or pick one", Icons.Outlined.DarkMode, trailing = {})
                Segments(
                    listOf(ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark"),
                    s.themeMode,
                ) { m -> change { it.copy(themeMode = m) } }
                CardDivider()
                SwitchRow(
                    Icons.Outlined.Wallpaper, "Wallpaper colors",
                    if (supportsDynamicColor) "Material You colors from your wallpaper" else "Needs Android 12 or newer",
                    s.dynamicColor && supportsDynamicColor, enabled = supportsDynamicColor,
                ) { v -> change { it.copy(dynamicColor = v) } }
                CardDivider()
                SwitchRow(
                    Icons.Outlined.ColorLens, "Orange accent",
                    "Keep Xiaomi.EU orange for buttons and highlights",
                    s.orangeAccent || !(s.dynamicColor && supportsDynamicColor),
                    enabled = s.dynamicColor && supportsDynamicColor,
                ) { v -> change { it.copy(orangeAccent = v) } }
            }

            SectionLabel("Notifications")
            SectionCard {
                SwitchRow(Icons.Outlined.Notifications, "Forum alerts", "Replies, quotes, mentions and reactions",
                    s.notifyAlerts) { v -> change(reschedule = true) { it.copy(notifyAlerts = v) } }
                CardDivider()
                SwitchRow(Icons.Outlined.Mail, "Direct messages", "New messages in your conversations",
                    s.notifyConversations) { v -> change(reschedule = true) { it.copy(notifyConversations = v) } }
                if (s.notifyAlerts || s.notifyConversations) {
                    CardDivider()
                    CardRow("Check every", "Android may delay checks to save battery", Icons.Outlined.Schedule, trailing = {})
                    Segments(
                        listOf(15 to "15 min", 30 to "30 min", 60 to "1 h", 180 to "3 h"),
                        s.forumCheckMinutes,
                    ) { m -> change(reschedule = true) { it.copy(forumCheckMinutes = m) } }
                }
                CardDivider()
                CardRow("ROM release alerts", "Choose devices and frequency", Icons.Outlined.SystemUpdate) { nav.tab("roms") }
                CardDivider()
                CardRow(
                    "Sounds and channels",
                    if (Notifier.allowed(context)) "Android notification settings" else "Notifications are off for this app",
                    Icons.Outlined.NotificationsActive,
                ) {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    )
                }
            }
            Text(
                "Fetching new alerts marks them as seen on the website too.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )

            SectionLabel("Updates")
            SectionCard {
                CardRow("Channel", "Stable is tested; Dev gets every change first", Icons.Outlined.Update, trailing = {})
                Segments(
                    listOf(Updater.CHANNEL_STABLE to "Stable", Updater.CHANNEL_DEV to "Dev"),
                    s.updateChannel,
                ) { c -> change { it.copy(updateChannel = c) } }
                CardDivider()
                SwitchRow(Icons.Outlined.SystemUpdate, "Check automatically", "On launch and twice a day",
                    s.autoCheckUpdates) { v -> change(reschedule = true) { it.copy(autoCheckUpdates = v) } }
                CardDivider()
                Column(Modifier.padding(vertical = 12.dp)) { UpdatePanel(s.updateChannel) }
            }
            Text(
                "Updates come from GitHub releases. If GitHub's API limit is reached, the app switches to the " +
                    "release download mirror automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )

            SectionLabel("About")
            SectionCard {
                CardRow("XEU Forums ${BuildConfig.VERSION_NAME}",
                    "Build ${BuildConfig.VERSION_CODE} · ${BuildConfig.CHANNEL} channel", Icons.Outlined.Info, trailing = {})
                CardDivider()
                CardRow("Source code and releases", "github.com/${BuildConfig.GITHUB_REPO}", Icons.Outlined.Code) {
                    nav.web("https://github.com/${BuildConfig.GITHUB_REPO}", "XEU Forums on GitHub")
                }
            }
            Text(
                "Unofficial app. Not affiliated with Xiaomi or xiaomi.eu.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(20.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun <T> Segments(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(start = 68.dp, end = 14.dp, bottom = 12.dp)) {
        options.forEachIndexed { i, (value, label) ->
            SegmentedButton(
                selected = selected == value,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
            ) { Text(label, maxLines = 1) }
        }
    }
}

@Composable
private fun SwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    CardRow(
        title, subtitle, icon,
        trailing = { Switch(checked = checked, onCheckedChange = onChange, enabled = enabled) },
        onClick = if (enabled) { { onChange(!checked) } } else null,
    )
}
