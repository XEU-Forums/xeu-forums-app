package dev.xeu.forums.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Last-seen header user and profile, so the Account tab shows instantly and refreshes in the background. */
class ProfileCache(context: Context) {
    private val prefs = context.getSharedPreferences("profile_cache", Context.MODE_PRIVATE)

    fun load(): Pair<Me, Profile?>? = runCatching {
        val o = JSONObject(prefs.getString("data", null) ?: return null)
        val m = o.getJSONObject("me")
        val me = Me(m.getInt("id"), m.getString("name"), m.optString("avatar").ifBlank { null }, m.optInt("alerts"), m.optInt("convos"))
        val p = o.optJSONObject("profile")?.let { p ->
            val st = p.optJSONArray("stats") ?: JSONArray()
            Profile(
                userId = p.getInt("id"), username = p.getString("name"), userTitle = p.optString("title"),
                avatarUrl = p.optString("avatar").ifBlank { null }, bannerUrl = p.optString("banner").ifBlank { null },
                stats = (0 until st.length()).map { st.getJSONArray(it).let { kv -> kv.getString(0) to kv.getString(1) } },
                url = p.optString("url"),
            )
        }
        me to p
    }.getOrNull()

    fun save(me: Me, profile: Profile?) {
        val o = JSONObject().put("me", JSONObject().put("id", me.userId).put("name", me.username)
            .put("avatar", me.avatarUrl ?: "").put("alerts", me.unreadAlerts).put("convos", me.unreadConversations))
        profile?.let { p ->
            o.put("profile", JSONObject().put("id", p.userId).put("name", p.username).put("title", p.userTitle)
                .put("avatar", p.avatarUrl ?: "").put("banner", p.bannerUrl ?: "").put("url", p.url)
                .put("stats", JSONArray().apply { p.stats.forEach { (k, v) -> put(JSONArray().put(k).put(v)) } }))
        }
        prefs.edit().putString("data", o.toString()).apply()
    }

    fun clear() = prefs.edit().remove("data").apply()
}
