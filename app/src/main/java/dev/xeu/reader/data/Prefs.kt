package dev.xeu.reader.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.store: DataStore<Preferences> by preferencesDataStore("xeu_prefs")

data class TrackerSettings(
    val enabled: Boolean = true,
    val intervalHours: Int = 6,
    val notifyAllReleaseThreads: Boolean = true,
)

class Prefs(private val context: Context) {
    private object K {
        val devices = stringPreferencesKey("devices")
        val seen = stringSetPreferencesKey("seen_rom_ids")
        val seeded = booleanPreferencesKey("seeded")
        val enabled = booleanPreferencesKey("tracker_enabled")
        val interval = intPreferencesKey("tracker_interval_h")
        val notifyAll = booleanPreferencesKey("notify_all_release_threads")
        val latestRoms = stringPreferencesKey("latest_roms")
        val lastCheck = stringPreferencesKey("last_check")
    }

    val devices: Flow<List<Device>> = context.store.data.map { decodeDevices(it[K.devices]) }
    val tracker: Flow<TrackerSettings> = context.store.data.map {
        TrackerSettings(it[K.enabled] ?: true, it[K.interval] ?: 6, it[K.notifyAll] ?: true)
    }
    val latestRoms: Flow<List<FeedItem>> = context.store.data.map { decodeFeed(it[K.latestRoms]) }
    val lastCheck: Flow<Long> = context.store.data.map { it[K.lastCheck]?.toLongOrNull() ?: 0L }

    suspend fun devicesNow() = devices.first()
    suspend fun trackerNow() = tracker.first()

    suspend fun setDevices(list: List<Device>) = context.store.edit { it[K.devices] = encodeDevices(list) }

    suspend fun toggleDevice(d: Device) {
        val cur = devicesNow()
        setDevices(if (cur.any { it.forumId == d.forumId }) cur.filter { it.forumId != d.forumId } else cur + d)
    }

    suspend fun setTracker(s: TrackerSettings) = context.store.edit {
        it[K.enabled] = s.enabled; it[K.interval] = s.intervalHours; it[K.notifyAll] = s.notifyAllReleaseThreads
    }

    suspend fun seen(): Set<String> = context.store.data.first()[K.seen] ?: emptySet()
    suspend fun isSeeded(): Boolean = context.store.data.first()[K.seeded] ?: false

    suspend fun markSeen(ids: Collection<String>) = context.store.edit {
        val merged = ((it[K.seen] ?: emptySet()) + ids).toList()
        it[K.seen] = merged.takeLast(1500).toSet()
        it[K.seeded] = true
    }

    suspend fun saveLatest(items: List<FeedItem>) = context.store.edit {
        it[K.latestRoms] = encodeFeed(items.take(60))
        it[K.lastCheck] = (System.currentTimeMillis() / 1000).toString()
    }

    private fun encodeDevices(list: List<Device>) = JSONArray().apply {
        list.forEach { d ->
            put(JSONObject().put("id", d.forumId).put("name", d.name).put("url", d.url)
                .put("codenames", JSONArray(d.codenames)))
        }
    }.toString()

    private fun decodeDevices(s: String?): List<Device> = runCatching {
        val a = JSONArray(s ?: return emptyList())
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            val cn = o.optJSONArray("codenames") ?: JSONArray()
            Device(o.getInt("id"), o.getString("name"), o.getString("url"),
                (0 until cn.length()).map { cn.getString(it) })
        }
    }.getOrDefault(emptyList())

    private fun encodeFeed(list: List<FeedItem>) = JSONArray().apply {
        list.forEach { f ->
            put(JSONObject().put("g", f.guid).put("t", f.title).put("l", f.link)
                .put("e", f.epoch).put("s", f.source).put("a", f.author))
        }
    }.toString()

    private fun decodeFeed(s: String?): List<FeedItem> = runCatching {
        val a = JSONArray(s ?: return emptyList())
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            FeedItem(o.getString("g"), o.getString("t"), o.getString("l"), o.getLong("e"),
                o.getString("s"), o.optString("a"))
        }
    }.getOrDefault(emptyList())
}
