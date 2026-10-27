package dev.xeu.forums.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.xeu.forums.Nav
import dev.xeu.forums.data.ForumRepository
import dev.xeu.forums.data.Prefs
import dev.xeu.forums.data.TrackerSettings
import dev.xeu.forums.work.RomTracker
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

    fun update(s: TrackerSettings) = scope.launch {
        prefs.setTracker(s)
        RomTracker.schedule(context, s.enabled, s.intervalHours)
    }

    fun checkNow() {
        if (checking) return
        checking = true; error = null
        scope.launch {
            try { RomTracker.notify(context, RomTracker.run(context)) }
            catch (e: Exception) { error = friendlyError(e) }
            finally { checking = false }
        }
    }

    LaunchedEffect(devices) { if (latest.isEmpty() && devices.isNotEmpty()) checkNow() }

    val files = latest.filter { it.source == "SourceForge" }
    val threads = latest.filter { it.source != "SourceForge" }
    val accents = LocalAccents.current

    Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0)) { pad ->
        RefreshBox(checking, ::checkNow, Modifier.padding(pad).fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    HeroHeader {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("ROM TRACKER", style = SectionLabelStyle, color = accents.onHero.copy(alpha = 0.8f))
                                Text("Latest builds", style = MaterialTheme.typography.headlineMedium, color = accents.onHero)
                            }
                            Switch(
                                settings.enabled, { update(settings.copy(enabled = it)) },
                                colors = SwitchDefaults.colors(
                                    checkedTrackColor = Color.White, checkedThumbColor = Brand.OrangeDeep,
                                    uncheckedTrackColor = Color.White.copy(alpha = 0.3f), uncheckedBorderColor = Color.Transparent,
                                    uncheckedThumbColor = Color.White,
                                ),
                            )
                        }
                        Text(
                            when {
                                checking -> "Checking now…"
                                lastCheck > 0 -> "Last checked ${relativeTime(lastCheck)}" +
                                    if (settings.enabled) " · every ${settings.intervalHours}h" else " · alerts off"
                                else -> "Not checked yet"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = accents.onHero.copy(alpha = 0.9f),
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        if (settings.enabled) Row(
                            Modifier.padding(top = 14.dp).horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf(1, 3, 6, 12, 24).forEach { h ->
                                FilterChip(
                                    selected = settings.intervalHours == h,
                                    onClick = { update(settings.copy(intervalHours = h)) },
                                    label = { Text("${h}h") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = Color.White.copy(alpha = 0.16f),
                                        labelColor = Color.White,
                                        selectedContainerColor = Color.White,
                                        selectedLabelColor = Brand.OrangeDeep,
                                    ),
                                    border = null,
                                )
                            }
                        }
                        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = ::checkNow, enabled = !checking,
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Brand.OrangeDeep),
                            ) {
                                if (checking) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Brand.OrangeDeep)
                                else Icon(Icons.Outlined.Refresh, null, Modifier.size(18.dp))
                                Spacer(Modifier.size(8.dp))
                                Text("Check now")
                            }
                            TextButton(onClick = { nav.devicePicker() }) {
                                Text("Devices (${devices.size})", color = Color.White)
                            }
                        }
                    }
                }
                error?.let { item { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(20.dp)) } }

                item {
                    SectionLabel("Alerts for") {
                        Switch(
                            settings.notifyAllReleaseThreads,
                            { update(settings.copy(notifyAllReleaseThreads = it)) },
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                    Text(
                        if (settings.notifyAllReleaseThreads) "Every new release thread, plus builds for your devices"
                        else "Only builds and threads for your devices",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }

                item { SectionLabel("Builds for your devices") }
                item {
                    SectionCard {
                        when {
                            devices.isEmpty() -> CardRow(
                                "Choose your devices", "Get alerts when new builds appear", Icons.Outlined.PhoneAndroid,
                            ) { nav.devicePicker() }
                            files.isEmpty() -> CardRow(
                                "No matching builds yet", "Pull down to check again", Icons.Outlined.Download, trailing = {},
                            )
                            else -> files.forEachIndexed { i, f ->
                                if (i > 0) CardDivider()
                                val device = ForumRepository.matchesDevice(f, devices)
                                val folder = f.title.substringBeforeLast('/').substringAfterLast('/')
                                CardRow(
                                    f.title.substringAfterLast('/'),
                                    listOfNotNull(device?.name, folder, relativeTime(f.epoch))
                                        .filter { it.isNotBlank() }.joinToString(" · "),
                                    Icons.Outlined.Download,
                                ) { nav.web(f.link, f.title.substringAfterLast('/')) }
                            }
                        }
                    }
                }

                item { SectionLabel("Release threads and announcements") }
                item {
                    SectionCard {
                        if (threads.isEmpty()) CardRow("Nothing yet", "Pull down to check", Icons.Outlined.Campaign, trailing = {})
                        threads.forEachIndexed { i, t ->
                            if (i > 0) CardDivider()
                            CardRow(
                                t.title,
                                listOf(t.source, t.author, relativeTime(t.epoch)).filter { it.isNotBlank() }.joinToString(" · "),
                                Icons.Outlined.Campaign,
                            ) { nav.link(t.link) }
                        }
                    }
                }
                item {
                    Text(
                        "Builds come from xiaomi.eu's public SourceForge feed. Always read the release thread before flashing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(20.dp),
                    )
                }
            }
        }
    }
}
