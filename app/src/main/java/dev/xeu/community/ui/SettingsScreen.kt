package dev.xeu.community.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.xeu.community.BuildConfig
import dev.xeu.community.Nav
import dev.xeu.community.data.AppSettings
import dev.xeu.community.data.SettingsStore
import dev.xeu.community.data.ThemeMode
import dev.xeu.community.update.Updater
import dev.xeu.community.work.Notifier
import dev.xeu.community.work.Scheduler
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

    ScreenScaffold(title = "Settings", onBack = { nav.back() }) { mod ->
        Column(mod.verticalScroll(rememberScrollState())) {
            Header(Icons.Outlined.Palette, "Appearance")
            Text("Theme", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(16.dp, 8.dp)) {
                val modes = listOf(ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark")
                modes.forEachIndexed { i, (mode, label) ->
                    SegmentedButton(
                        selected = s.themeMode == mode,
                        onClick = { change { it.copy(themeMode = mode) } },
                        shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                    ) { Text(label) }
                }
            }
            SwitchRow(
                "Dynamic color",
                if (supportsDynamicColor) "Use colors from your wallpaper (Material You)"
                else "Needs Android 12 or newer",
                checked = s.dynamicColor && supportsDynamicColor,
                enabled = supportsDynamicColor,
            ) { v -> change { it.copy(dynamicColor = v) } }

            Header(Icons.Outlined.Notifications, "Notifications")
            SwitchRow("Forum alerts", "Replies, quotes, mentions and reactions", s.notifyAlerts) { v ->
                change(reschedule = true) { it.copy(notifyAlerts = v) }
            }
            SwitchRow("Direct messages", "New messages in your conversations", s.notifyConversations) { v ->
                change(reschedule = true) { it.copy(notifyConversations = v) }
            }
            if (s.notifyAlerts || s.notifyConversations) {
                Text("Check every", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(16.dp, 8.dp)) {
                    val opts = listOf(15 to "15 min", 30 to "30 min", 60 to "1 h", 180 to "3 h")
                    opts.forEachIndexed { i, (m, label) ->
                        SegmentedButton(
                            selected = s.forumCheckMinutes == m,
                            onClick = { change(reschedule = true) { it.copy(forumCheckMinutes = m) } },
                            shape = SegmentedButtonDefaults.itemShape(i, opts.size),
                        ) { Text(label) }
                    }
                }
                Text(
                    "Fetching new alerts marks them as seen on the website too. Android may delay checks to save battery.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            ListItem(
                headlineContent = { Text("ROM release alerts") },
                supportingContent = { Text("Set up in the ROMs tab") },
                modifier = Modifier.clickable { nav.tab("roms") },
            )
            ListItem(
                headlineContent = { Text("System notification settings") },
                supportingContent = {
                    Text(if (Notifier.allowed(context)) "Sounds, importance and channels" else "Notifications are off for this app")
                },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) },
                modifier = Modifier.clickable {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    )
                },
            )

            Header(Icons.Outlined.SystemUpdate, "Updates")
            Text("Channel", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(16.dp, 8.dp)) {
                val ch = listOf(Updater.CHANNEL_STABLE to "Stable", Updater.CHANNEL_DEV to "Dev")
                ch.forEachIndexed { i, (c, label) ->
                    SegmentedButton(
                        selected = s.updateChannel == c,
                        onClick = { change { it.copy(updateChannel = c) } },
                        shape = SegmentedButtonDefaults.itemShape(i, ch.size),
                    ) { Text(label) }
                }
            }
            SwitchRow("Check automatically", "On launch and twice a day", s.autoCheckUpdates) { v ->
                change(reschedule = true) { it.copy(autoCheckUpdates = v) }
            }
            UpdatePanel(s.updateChannel)
            Text(
                "Updates come from GitHub releases. If GitHub's API limit is reached, the app falls back to " +
                    "the release download mirror automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp, 8.dp),
            )

            Header(Icons.Outlined.Code, "About")
            ListItem(
                headlineContent = { Text("XEU Community ${BuildConfig.VERSION_NAME}") },
                supportingContent = { Text("Build ${BuildConfig.VERSION_CODE} · ${BuildConfig.CHANNEL} channel") },
            )
            ListItem(
                headlineContent = { Text("Source code & releases") },
                supportingContent = { Text("github.com/${BuildConfig.GITHUB_REPO}") },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) },
                modifier = Modifier.clickable { openExternal(context, "https://github.com/${BuildConfig.GITHUB_REPO}") },
            )
            Text(
                "Unofficial app. Not affiliated with Xiaomi or xiaomi.eu.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Header(icon: ImageVector, title: String) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange, enabled = enabled) },
        modifier = Modifier.clickable(enabled = enabled) { onChange(!checked) },
    )
}
