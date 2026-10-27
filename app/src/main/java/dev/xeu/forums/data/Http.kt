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
        val raw = cm.getCookie(url.toString()) ?: return emptyList()
        return raw.split(';').mapNotNull { Cookie.parse(url, it.trim()) }
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
                chain.proceed(chain.request().newBuilder().header("User-Agent", userAgent).build())
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

    fun isLoggedIn(): Boolean = loggedIn.value ?: hasRememberCookie()

    private fun hasRememberCookie(): Boolean {
        val c = CookieManager.getInstance().getCookie(BASE) ?: return false
        return c.split(';').any { it.trim().startsWith("xf_user=") }
    }


}
