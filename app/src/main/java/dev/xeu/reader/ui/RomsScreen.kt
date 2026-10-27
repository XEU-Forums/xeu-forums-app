package dev.xeu.reader.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.xeu.reader.Nav
import dev.xeu.reader.data.ForumRepository
import dev.xeu.reader.data.Prefs
import dev.xeu.reader.data.TrackerSettings
import dev.xeu.reader.work.RomTracker
import kotlinx.coroutines.launch

@Composable
fun RomsScreen(nav: Nav) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val scope = rememberCoroutineScope()
    val settings by prefs.tracker.collectAsState(initial = TrackerSettings())
    val devices by prefs.devices.collectAsState(initial = emptyList())
    val latest by prefs.latestRoms.collectAsState(initial = emptyList())
    val lastCheck by prefs.lastCheck.collectAsState(initial = 0L)
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    var notifGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notifGranted = it
    }

    fun update(s: TrackerSettings) = scope.launch {
        prefs.setTracker(s)
        RomTracker.schedule(context, s.enabled, s.intervalHours)
    }

    fun checkNow() {
        checking = true; error = null
        scope.launch {
            try {
                RomTracker.notify(context, RomTracker.run(context))
            } catch (e: Exception) {
                error = friendlyError(e)
            } finally { checking = false }
        }
    }

    // First visit: fill the list without waiting for the background schedule.
    LaunchedEffect(devices) { if (latest.isEmpty() && devices.isNotEmpty() && !checking) checkNow() }

    val files = latest.filter { it.source == "SourceForge" }
    val threads = latest.filter { it.source != "SourceForge" }

    ScreenScaffold(title = "ROM tracker") { mod ->
        LazyColumn(mod) {
            item {
                Card(Modifier.fillMaxWidth().padding(12.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Check for new builds", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            Switch(settings.enabled, { update(settings.copy(enabled = it)) })
                        }
                        if (settings.enabled) {
                            Text("Every", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(1, 3, 6, 12, 24).forEach { h ->
                                    FilterChip(settings.intervalHours == h, { update(settings.copy(intervalHours = h)) },
                                        { Text("${h}h") })
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                Text(
                                    "Also alert for every new release thread, not just my devices",
                                    Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                                )
                                Switch(settings.notifyAllReleaseThreads, {
                                    update(settings.copy(notifyAllReleaseThreads = it))
                                })
                            }
                            if (!notifGranted) {
                                TextButton(onClick = { permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                                    Text("Allow notifications")
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                            Text(
                                if (lastCheck > 0) "Last checked ${relativeTime(lastCheck)}" else "Not checked yet",
                                Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (checking) CircularProgressIndicator(Modifier.size(24.dp))
                            else Button(onClick = ::checkNow) { Text("Check now") }
                        }
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
            if (devices.isEmpty()) item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text("Choose your devices to track builds for them.")
                    TextButton(onClick = { nav.devicePicker() }) { Text("Choose devices") }
                }
            } else item {
                Text(
                    "Tracking: " + devices.joinToString { "${it.name} (${it.codenames.joinToString("/")})" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            item { SectionHeader("Builds for your devices") }
            if (files.isEmpty()) item {
                Text("No matching builds in the latest uploads yet.", Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodyMedium)
            }
            items(files, key = { "f-${it.guid}" }) { f ->
                val device = ForumRepository.matchesDevice(f, devices)
                Row(
                    Modifier.fillMaxWidth().clickable { openExternal(context, f.link) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Download, null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(f.title.substringAfterLast('/'), style = MaterialTheme.typography.titleSmall,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(device?.name, f.title.substringBeforeLast('/').substringAfterLast('/'),
                                relativeTime(f.epoch)).filter { it.isNotBlank() }.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                HorizontalDivider()
            }
            item { SectionHeader("Release threads & announcements") }
            items(threads, key = { "t-${it.guid}" }) { t -> FeedRow(t) { nav.link(t.link) } }
            item {
                Text(
                    "Builds come from xiaomi.eu's public SourceForge file feed. Always read the release thread before flashing.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}
