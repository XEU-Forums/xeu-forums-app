package dev.xeu.community.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.xeu.community.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore("xeu_settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val updateChannel: String = BuildConfig.CHANNEL,
    val autoCheckUpdates: Boolean = true,
    val notifyAlerts: Boolean = true,
    val notifyConversations: Boolean = true,
    val forumCheckMinutes: Int = 30,
)

class SettingsStore(private val context: Context) {
    private object K {
        val theme = stringPreferencesKey("theme_mode")
        val dynamic = booleanPreferencesKey("dynamic_color")
        val channel = stringPreferencesKey("update_channel")
        val autoUpdate = booleanPreferencesKey("auto_check_updates")
        val alerts = booleanPreferencesKey("notify_alerts")
        val convos = booleanPreferencesKey("notify_conversations")
        val forumMinutes = intPreferencesKey("forum_check_minutes")
        val lastAlertId = stringPreferencesKey("last_alert_id")
        val seenConvos = stringPreferencesKey("seen_conversations")
        val skippedVersion = intPreferencesKey("skipped_version")
    }

    val settings: Flow<AppSettings> = context.settingsStore.data.map { p ->
        AppSettings(
            themeMode = runCatching { ThemeMode.valueOf(p[K.theme] ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM),
            dynamicColor = p[K.dynamic] ?: true,
            updateChannel = p[K.channel] ?: BuildConfig.CHANNEL,
            autoCheckUpdates = p[K.autoUpdate] ?: true,
            notifyAlerts = p[K.alerts] ?: true,
            notifyConversations = p[K.convos] ?: true,
            forumCheckMinutes = p[K.forumMinutes] ?: 30,
        )
    }

    suspend fun now(): AppSettings = settings.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        val s = transform(now())
        context.settingsStore.edit { p ->
            p[K.theme] = s.themeMode.name
            p[K.dynamic] = s.dynamicColor
            p[K.channel] = s.updateChannel
            p[K.autoUpdate] = s.autoCheckUpdates
            p[K.alerts] = s.notifyAlerts
            p[K.convos] = s.notifyConversations
            p[K.forumMinutes] = s.forumCheckMinutes
        }
    }

    // ---- notification bookkeeping (per account) ----
    suspend fun lastAlertId(account: String): Long =
        context.settingsStore.data.first()[stringPreferencesKey("last_alert_$account")]?.toLongOrNull() ?: -1L

    suspend fun setLastAlertId(account: String, id: Long) = context.settingsStore.edit {
        it[stringPreferencesKey("last_alert_$account")] = id.toString()
    }

    /** Conversation id -> last seen reply count, so each new message notifies once. */
    suspend fun seenConversations(account: String): Map<Int, String> {
        val raw = context.settingsStore.data.first()[stringPreferencesKey("seen_convos_$account")] ?: return emptyMap()
        return raw.split('|').mapNotNull { e ->
            val (k, v) = e.split('=', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
            k.toIntOrNull()?.let { it to v }
        }.toMap()
    }

    suspend fun setSeenConversations(account: String, map: Map<Int, String>) = context.settingsStore.edit {
        it[stringPreferencesKey("seen_convos_$account")] =
            map.entries.toList().takeLast(200).joinToString("|") { (k, v) -> "$k=${v.replace("|", "").replace("=", "")}" }
    }

    suspend fun skippedVersion(): Int = context.settingsStore.data.first()[K.skippedVersion] ?: 0
    suspend fun skipVersion(code: Int) = context.settingsStore.edit { it[K.skippedVersion] = code }
}
