package dev.xeu.forums.data

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

class Prefs(private val context: Context) {
    private object K {
        val devices = stringPreferencesKey("devices")
    }

    val devices: Flow<List<Device>> = context.store.data.map { decodeDevices(it[K.devices]) }

    suspend fun devicesNow() = devices.first()

    suspend fun setDevices(list: List<Device>) = context.store.edit { it[K.devices] = encodeDevices(list) }

    suspend fun toggleDevice(d: Device) {
        val cur = devicesNow()
        setDevices(if (cur.any { it.forumId == d.forumId }) cur.filter { it.forumId != d.forumId } else cur + d)
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

}
