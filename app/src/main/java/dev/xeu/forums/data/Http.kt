package dev.xeu.forums.data

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

const val BASE = "https://www.xiaomi.eu/community/"

private val bareHost = Regex("""^(https?://)xiaomi\.eu(?=[/:?#]|$)""", RegexOption.IGNORE_CASE)

/**
 * The forum answers on both xiaomi.eu and www.xiaomi.eu, and feeds link to either. Cookies set on
 * one host aren't sent to the other, so every forum URL is normalized to www.xiaomi.eu.
 */
/** Forum pages the app renders natively. Anything else on Xiaomi.EU isn't offered in the app. */
fun isNativeForumLink(url: String): Boolean {
    val u = normalizeForumUrl(url)
    if (!Regex("""^https?://www\.xiaomi\.eu/community/""").containsMatchIn(u)) return false
    return listOf("/threads/", "/posts/", "/goto/post", "/conversations/", "/forums/", "/categories/",
        "/members/", "/whats-new/", "/watched/threads", "/account/alerts").any { it in u } ||
        u.trimEnd('/') == BASE.trimEnd('/')
}

fun normalizeForumUrl(url: String): String = bareHost.replace(url.trim()) { "${it.groupValues[1]}www.xiaomi.eu" }

/**
 * Shares cookies with the WebView, so logging in through the login page
 * also logs in every request the app makes.
 */
class WebViewCookieJar : CookieJar {
    private val cm: CookieManager get() = CookieManager.getInstance()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { cm.setCookie(url.toString(), it.toString()) }
        cm.flush()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val sources = if (url.host.endsWith("xiaomi.eu")) {
            // Include cookies stored for either host so a session from one works on the other.
            listOf(url.toString(), url.toString().replaceFirst("://www.xiaomi.eu", "://xiaomi.eu"),
                url.toString().replaceFirst("://xiaomi.eu", "://www.xiaomi.eu")).distinct()
        } else listOf(url.toString())
        return sources.mapNotNull { cm.getCookie(it) }
            .flatMap { it.split(';') }
            .mapNotNull { Cookie.parse(url, it.trim()) }
            .distinctBy { it.name }
    }
}

object Http {
    lateinit var client: OkHttpClient
        private set
    lateinit var userAgent: String
        private set

    fun init(context: Context) {
        if (::client.isInitialized) return
        userAgent = runCatching { WebSettings.getDefaultUserAgent(context) }
            .getOrDefault("Mozilla/5.0 (Linux; Android) Mobile") + " XEUForums/1.0"
        client = OkHttpClient.Builder()
            .cookieJar(WebViewCookieJar())
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val req = chain.request()
                val b = req.newBuilder().header("User-Agent", userAgent)
                if (req.url.host.equals("xiaomi.eu", ignoreCase = true)) {
                    b.url(req.url.newBuilder().host("www.xiaomi.eu").build())
                }
                chain.proceed(b.build())
            }
            .build()
    }

    suspend fun get(url: String): String = fetch(url).first

    /** Returns the body and the final URL after redirects. */
    suspend fun fetch(url: String): Pair<String, String> = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code} for $url")
            val body = r.body?.string() ?: ""
            if (r.request.url.host.endsWith("xiaomi.eu")) observe(body)
            body to r.request.url.toString()
        }
    }

    /** XenForo AJAX-style POST; returns the raw JSON response body. */
    suspend fun postAjax(url: String, body: RequestBody, referer: String): String =
        withContext(Dispatchers.IO) {
            val req = Request.Builder().url(url).post(body)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .header("Referer", referer)
                .build()
            client.newCall(req).execute().use { r -> r.body?.string() ?: "" }
        }

    /**
     * Login state as last seen on a forum page. Null until the first page loads;
     * then we fall back to the "stay logged in" cookie.
     */
    val loggedIn = mutableStateOf<Boolean?>(null)

    /** Unread alerts and conversations from the last forum page we loaded. */
    val unreadAlerts = mutableStateOf(0)
    val unreadConversations = mutableStateOf(0)

    private val badgeTag = Regex("""<a\b[^>]*js-badge--(alerts|conversations)[^>]*>""")
    private val badgeValue = Regex("""data-badge="(\d+)"""")

    /** Keeps login state and unread badges current from every page we fetch. */
    private fun observe(body: String) {
        when {
            body.contains("data-logged-in=\"true\"") -> loggedIn.value = true
            body.contains("data-logged-in=\"false\"") -> {
                loggedIn.value = false; unreadAlerts.value = 0; unreadConversations.value = 0
            }
            else -> return
        }
        badgeTag.findAll(body).forEach { m ->
            val n = badgeValue.find(m.value)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            if (m.groupValues[1] == "alerts") unreadAlerts.value = n else unreadConversations.value = n
        }
    }

    /**
     * Copies every forum cookie onto the shared .xiaomi.eu domain, so the WebView (login, account
     * pages) and the app's own requests always see the same session on both hosts.
     */
    fun unifyForumCookies() {
        val cm = CookieManager.getInstance()
        val all = listOf("https://www.xiaomi.eu/", "https://xiaomi.eu/").mapNotNull { cm.getCookie(it) }
            .flatMap { it.split(';') }.map { it.trim() }.filter { '=' in it }
            .distinctBy { it.substringBefore('=') }
        all.forEach { kv ->
            cm.setCookie("https://www.xiaomi.eu/", "$kv; Domain=.xiaomi.eu; Path=/; Secure; Max-Age=31536000")
        }
        cm.flush()
    }

    fun isLoggedIn(): Boolean = loggedIn.value ?: hasRememberCookie()

    private fun hasRememberCookie(): Boolean {
        val cm = CookieManager.getInstance()
        return listOf(BASE, "https://xiaomi.eu/community/").mapNotNull { cm.getCookie(it) }
            .any { c -> c.split(';').any { it.trim().startsWith("xf_user=") } }
    }


}
