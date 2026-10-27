package dev.xeu.forums.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.FormBody
import org.json.JSONObject

const val RELEASE_FORUM = BASE + "forums/rom-releases.103/"

class PostException(msg: String) : Exception(msg)

object ForumRepository {

    /** Strips page/post suffixes so "page-N" can be appended cleanly. */
    fun baseUrl(url: String): String {
        var u = normalizeForumUrl(url).substringBefore('#').substringBefore('?')
        u = u.replace(Regex("""/(page-\d+|post-\d+|latest|unread)/?$"""), "/")
        return if (u.endsWith("/")) u else "$u/"
    }

    private fun pageUrl(url: String, page: Int) = if (page <= 1) baseUrl(url) else baseUrl(url) + "page-$page"

    suspend fun index(): ForumIndex {
        val (html, final) = Http.fetch(BASE)
        return ForumParser.index(ForumParser.doc(html, final))
    }

    /**
     * Forum search. The first call runs the search (the forum redirects to a results URL);
     * later pages reuse that URL.
     */
    suspend fun search(query: String, titlesOnly: Boolean, resultsUrl: String?, page: Int): SearchPage {
        val u = if (resultsUrl != null) {
            resultsUrl.substringBefore("&page=").substringBefore("?page=").let {
                if (page > 1) it + (if ('?' in it) "&" else "?") + "page=$page" else it
            }
        } else {
            val q = java.net.URLEncoder.encode(query, "UTF-8")
            BASE + "search/search?keywords=$q&order=date" + if (titlesOnly) "&c[title_only]=1" else ""
        }
        val (html, final) = Http.fetch(u)
        return ForumParser.search(ForumParser.doc(html, final), final)
    }

    const val NEW_POSTS = BASE + "whats-new/posts/"

    suspend fun allDeviceForums(): List<Node> {
        val doc = ForumParser.doc(Http.get(BASE), BASE)
        return ForumParser.allNodes(doc).filter { it.codenames.isNotEmpty() }
    }

    suspend fun forum(url: String, page: Int): ForumPage {
        val (html, finalUrl) = Http.fetch(pageUrl(url, page))
        return ForumParser.forumPage(ForumParser.doc(html, finalUrl), finalUrl)
    }

    /** page = 0 loads [url] exactly as given (e.g. a ".../latest" or "post-123" link). */
    suspend fun thread(url: String, page: Int): ThreadPage {
        val u = if (page == 0) url else pageUrl(url, page)
        val (html, finalUrl) = Http.fetch(u)
        return ForumParser.threadPage(ForumParser.doc(html, finalUrl), finalUrl)
    }

    suspend fun rss(url: String, source: String): List<FeedItem> =
        ForumParser.rss(Http.get(url), source)

    suspend fun deviceFeed(devices: List<Device>): List<FeedItem> = coroutineScope {
        devices.map { d ->
            async { runCatching { rss(baseUrl(d.url) + "index.rss", d.name) }.getOrDefault(emptyList()) }
        }.awaitAll().flatten().distinctBy { it.link }.sortedByDescending { it.epoch }
    }

    private fun codenameRegex(code: String) =
        Regex("""(^|[^a-z0-9])${Regex.escape(code.lowercase())}([^a-z0-9]|$)""", RegexOption.IGNORE_CASE)

    fun matchesDevice(item: FeedItem, devices: List<Device>): Device? =
        devices.firstOrNull { d -> d.codenames.any { codenameRegex(it).containsMatchIn(item.title) } }

    // ---- Posting (requires login) ----

    private fun checkJson(raw: String): String {
        val json = runCatching { JSONObject(raw) }.getOrNull()
            ?: throw PostException("Unexpected response from the forum. Try again, or check you're still logged in.")
        if (json.optString("status") == "ok") return json.optString("redirect")
        val errs = json.opt("errors")
        val msg = when (errs) {
            is org.json.JSONArray -> (0 until errs.length()).joinToString("\n") { errs.optString(it) }
            is JSONObject -> errs.keys().asSequence().joinToString("\n") { errs.optString(it) }
            else -> json.optString("message", "The forum rejected the post.")
        }
        throw PostException(org.jsoup.Jsoup.parse(msg).text())
    }

    suspend fun postReply(threadUrl: String, message: String): String {
        // Direct-message conversations have no "latest" route; their pages carry the reply form too.
        val latest = baseUrl(threadUrl) + if ("/conversations/" in threadUrl) "" else "latest"
        val html = Http.get(latest)
        val doc = ForumParser.doc(html, latest)
        if (!ForumParser.session(doc).loggedIn) throw PostException("You're not logged in.")
        val page = ForumParser.threadPage(doc, latest)
        val action = page.replyAction ?: throw PostException(
            if (page.locked) "This thread is closed for replies." else "You can't reply to this thread."
        )
        val body = FormBody.Builder()
            .add("message", message)
            .add("_xfToken", ForumParser.csrf(doc))
            .add("_xfResponseType", "json")
            .add("_xfWithData", "1")
            .add("_xfRequestUri", java.net.URI(latest).path)
            .build()
        return checkJson(Http.postAjax(action, body, latest))
    }

    suspend fun postThread(forumUrl: String, title: String, message: String): String {
        val formUrl = baseUrl(forumUrl) + "post-thread"
        val doc = ForumParser.doc(Http.get(formUrl), formUrl)
        if (!ForumParser.session(doc).loggedIn) throw PostException("You're not logged in.")
        val form = doc.selectFirst("form[action*=post-thread]")
            ?: throw PostException("You can't start threads in this forum.")
        val body = FormBody.Builder()
            .add("title", title)
            .add("message", message)
            .add("_xfToken", ForumParser.csrf(doc))
            .add("_xfResponseType", "json")
            .add("_xfWithData", "1")
            .build()
        return checkJson(Http.postAjax(form.absUrl("action").ifBlank { formUrl }, body, formUrl))
    }

    suspend fun session(): SessionInfo = ForumParser.session(ForumParser.doc(Http.get(BASE), BASE))

    // ---- Account ----

    /** The signed-in user, or null if the session isn't logged in. */
    suspend fun me(): Me? {
        val (html, final) = Http.fetch(BASE)
        return AccountParser.me(ForumParser.doc(html, final))
    }

    suspend fun profileByUrl(url: String): Profile {
        val (html, final) = Http.fetch(baseUrl(url))
        return AccountParser.profile(ForumParser.doc(html, final), final)
    }

    suspend fun profile(userId: Int): Profile {
        val (html, final) = Http.fetch(BASE + "members/$userId/")
        return AccountParser.profile(ForumParser.doc(html, final), final)
    }

    suspend fun alerts(): List<Alert> {
        val (html, final) = Http.fetch(BASE + "account/alerts")
        val doc = ForumParser.doc(html, final)
        if (!ForumParser.session(doc).loggedIn) throw PostException("You're not logged in.")
        return AccountParser.alerts(doc)
    }

    suspend fun markAlertsRead() {
        val (html, final) = Http.fetch(BASE + "account/alerts")
        val token = ForumParser.csrf(ForumParser.doc(html, final))
        val body = FormBody.Builder().add("_xfToken", token).add("_xfResponseType", "json").build()
        Http.postAjax(BASE + "account/alerts/mark-read", body, final)
    }

    suspend fun conversations(): List<Conversation> {
        val (html, final) = Http.fetch(BASE + "conversations/")
        val doc = ForumParser.doc(html, final)
        if (!ForumParser.session(doc).loggedIn) throw PostException("You're not logged in.")
        return AccountParser.conversations(doc)
    }

    suspend fun watchedThreads(page: Int): ForumPage {
        val u = BASE + "watched/threads" + if (page > 1) "?page=$page" else ""
        val (html, final) = Http.fetch(u)
        return AccountParser.watched(ForumParser.doc(html, final), final)
    }

    /** Ends the session on the forum, like tapping "Log out" on the website. */
    suspend fun logoutOnForum() {
        val (html, final) = Http.fetch(BASE)
        val token = ForumParser.csrf(ForumParser.doc(html, final))
        if (token.isNotBlank()) runCatching { Http.get(BASE + "logout/?t=" + java.net.URLEncoder.encode(token, "UTF-8")) }
    }
}
