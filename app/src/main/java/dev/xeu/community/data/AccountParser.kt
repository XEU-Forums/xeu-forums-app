package dev.xeu.community.data

import org.jsoup.nodes.Document

/** The signed-in user as shown in the forum's header. */
data class Me(
    val userId: Int,
    val username: String,
    val avatarUrl: String?,
    val unreadAlerts: Int,
    val unreadConversations: Int,
)

data class Profile(
    val userId: Int,
    val username: String,
    val userTitle: String,
    val avatarUrl: String?,
    val bannerUrl: String?,
    /** e.g. Messages, Reaction score, Points, Joined, Last seen — in page order. */
    val stats: List<Pair<String, String>>,
    val url: String,
)

data class Alert(
    val id: Long,
    val text: String,
    val link: String?,
    val avatarUrl: String?,
    val epoch: Long,
    val unread: Boolean,
)

data class Conversation(
    val id: Int,
    val title: String,
    val url: String,
    val participants: List<String>,
    val replies: String,
    val lastEpoch: Long,
    val lastSender: String,
    val unread: Boolean,
)

object AccountParser {

    private fun badge(doc: Document, cls: String): Int =
        doc.selectFirst(".js-badge--$cls, .p-navgroup-link--$cls")?.attr("data-badge")?.toIntOrNull() ?: 0

    fun me(doc: Document): Me? {
        if (doc.selectFirst("html")?.attr("data-logged-in") != "true") return null
        val link = doc.selectFirst(".p-navgroup-link--user") ?: doc.selectFirst("a[href*=/account/]")
        val avatar = link?.selectFirst(".avatar")
        val id = avatar?.attr("data-user-id")?.toIntOrNull()
            ?: doc.selectFirst("[data-user-id]")?.attr("data-user-id")?.toIntOrNull() ?: 0
        val name = link?.selectFirst(".p-navgroup-linkText")?.text()?.ifBlank { null }
            ?: avatar?.selectFirst("img")?.attr("alt")?.ifBlank { null }
            ?: "Member"
        return Me(
            userId = id,
            username = name,
            avatarUrl = avatar?.selectFirst("img")?.let { img ->
                img.absUrl("srcset").substringBefore(' ').ifBlank { img.absUrl("src") }
            }?.ifBlank { null },
            unreadAlerts = badge(doc, "alerts"),
            unreadConversations = badge(doc, "conversations"),
        )
    }

    fun profile(doc: Document, url: String): Profile {
        val header = doc.selectFirst(".memberHeader") ?: doc
        val stats = mutableListOf<Pair<String, String>>()
        header.select("dl.pairs").forEach { dl ->
            val k = dl.selectFirst("dt")?.text()?.trim().orEmpty()
            val v = dl.selectFirst("dd")?.text()?.trim().orEmpty()
            if (k.isNotBlank() && v.isNotBlank() && stats.none { it.first == k }) stats += k to v
        }
        val avatarImg = header.selectFirst(".memberHeader-avatar img")
        return Profile(
            userId = header.selectFirst("[data-user-id]")?.attr("data-user-id")?.toIntOrNull()
                ?: ForumParser.idFromUrl(url),
            username = header.selectFirst(".memberHeader-name .username, .memberHeader-name")?.text().orEmpty(),
            userTitle = header.selectFirst(".memberHeader-blurb .userTitle, .userTitle")?.text().orEmpty(),
            avatarUrl = avatarImg?.let { it.absUrl("srcset").substringBefore(' ').ifBlank { it.absUrl("src") } }
                ?.ifBlank { null },
            bannerUrl = header.selectFirst(".memberProfileBanner")?.attr("style")
                ?.let { Regex("""url\(['"]?([^'")]+)""").find(it)?.groupValues?.get(1) }
                ?.let { if (it.startsWith("http")) it else BASE.substringBefore("/community/") + it },
            stats = stats,
            url = url,
        )
    }

    fun alerts(doc: Document): List<Alert> = doc.select("[data-alert-id]").mapNotNull { li ->
        val id = li.attr("data-alert-id").toLongOrNull() ?: return@mapNotNull null
        val main = li.selectFirst(".contentRow-main")?.clone() ?: return@mapNotNull null
        main.select(".contentRow-minor, .contentRow-extra, .alert-action").remove()
        val link = li.selectFirst("a.fauxBlockLink-blockLink")?.absUrl("href")
            ?: li.select(".contentRow-main a[href]").firstOrNull { !it.hasClass("username") }?.absUrl("href")
        Alert(
            id = id,
            text = main.text().trim(),
            link = link?.ifBlank { null },
            avatarUrl = li.selectFirst(".contentRow-figure img")?.absUrl("src")?.ifBlank { null },
            epoch = li.selectFirst("time[data-time]")?.attr("data-time")?.toLongOrNull() ?: 0L,
            unread = li.hasClass("is-unread"),
        )
    }

    fun conversations(doc: Document): List<Conversation> = doc.select(".structItem--conversation").mapNotNull { el ->
        val a = el.selectFirst(".structItem-title a[href*=/conversations/]") ?: return@mapNotNull null
        val url = a.absUrl("href")
        val meta = el.select(".structItem-cell--meta dd").map { it.text() }
        Conversation(
            id = ForumParser.idFromUrl(url),
            title = a.text(),
            url = url,
            participants = el.select(".structItem-parts .username, .listInline .username").map { it.text() }.distinct(),
            replies = meta.getOrNull(0).orEmpty(),
            lastEpoch = el.selectFirst(".structItem-latestDate, .structItem-cell--latest time")
                ?.attr("data-time")?.toLongOrNull() ?: 0L,
            lastSender = el.selectFirst(".structItem-cell--latest .username")?.text().orEmpty(),
            unread = el.hasClass("is-unread"),
        )
    }

    /** Watched threads use the same list markup as a forum. */
    fun watched(doc: Document, url: String): ForumPage = ForumParser.forumPage(doc, url)
}
