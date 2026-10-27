package dev.xeu.forums.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser

/** Parses xiaomi.eu's XenForo 2 pages. Selectors follow the stock XF2 templates. */
object ForumParser {

    private val idRegex = Regex("""\.(\d+)/?(?:[?#].*)?$""")
    fun idFromUrl(url: String): Int =
        idRegex.find(url.substringBefore("/page-").substringBefore("/post-"))?.groupValues?.get(1)?.toIntOrNull() ?: 0

    fun doc(html: String, url: String): Document = Jsoup.parse(html, normalizeForumUrl(url))

    fun csrf(doc: Document): String = doc.selectFirst("html")?.attr("data-csrf").orEmpty()

    fun session(doc: Document): SessionInfo {
        val loggedIn = doc.selectFirst("html")?.attr("data-logged-in") == "true"
        val name = doc.selectFirst(".p-navgroup-link--user .p-navgroup-linkText")?.text()
        return SessionInfo(loggedIn, name?.takeIf { it.isNotBlank() })
    }

    private fun parseNode(el: Element): Node? {
        val a = el.selectFirst(".node-title a") ?: return null
        val url = a.absUrl("href")
        val stats = el.select(".node-stats dl dd").map { it.text() }
        val subs = el.select(".node-subNodeFlatList a, .node-subNodeMenu a.subNodeLink").map {
            Node(idFromUrl(it.absUrl("href")), it.text(), it.absUrl("href"),
                isCategory = it.absUrl("href").contains("/categories/"))
        }.distinctBy { it.url }
        return Node(
            id = idFromUrl(url),
            title = a.text(),
            url = url,
            description = el.selectFirst(".node-description")?.text().orEmpty(),
            threads = stats.getOrNull(0).orEmpty(),
            messages = stats.getOrNull(1).orEmpty(),
            isCategory = url.contains("/categories/") || el.hasClass("node--category"),
            hasNew = el.hasClass("node--unread"),
            subNodes = subs,
        )
    }

    /** Forum index or category page: sections of nodes. */
    fun nodeSections(doc: Document): List<NodeSection> {
        val sections = mutableListOf<NodeSection>()
        for (block in doc.select(".block")) {
            if (block.parents().any { it.hasClass("block") }) continue
            val nodes = block.select(".node").filter { n ->
                // only top-level nodes within this block
                n.parents().none { it.hasClass("node") }
            }.mapNotNull { parseNode(it) }
            if (nodes.isEmpty()) continue
            val headerEl = block.selectFirst(".block-header")
            val header = headerEl?.selectFirst("> a")?.text()?.ifBlank { null }
                ?: headerEl?.ownText()?.ifBlank { null }
                ?: headerEl?.text()
            sections += NodeSection(header?.takeIf { it.isNotBlank() } ?: "Sub-forums", nodes)
        }
        return sections
    }

    fun allNodes(doc: Document): List<Node> =
        nodeSections(doc).flatMap { s -> s.nodes + s.nodes.flatMap { it.subNodes } }.distinctBy { it.url }

    fun index(doc: Document): ForumIndex {
        val statsBlock = doc.select(".block").firstOrNull {
            it.hasClass("block--statistics") ||
                it.selectFirst(".block-minorHeader")?.text()?.contains("statistics", ignoreCase = true) == true
        }
        val stats = statsBlock?.select("dl.pairs")?.mapNotNull { dl ->
            val k = dl.selectFirst("dt")?.text()?.trim().orEmpty()
            val v = dl.selectFirst("dd")?.text()?.trim().orEmpty()
            if (k.isNotBlank() && v.isNotBlank() && !k.contains("latest", true)) k to v else null
        }.orEmpty()
        val online = doc.select(".block").firstOrNull {
            it.selectFirst(".block-minorHeader")?.text()?.contains("online", ignoreCase = true) == true
        }?.selectFirst(".block-footer")?.text()?.trim().orEmpty()
        return ForumIndex(nodeSections(doc), session(doc), stats, online)
    }

    fun search(doc: Document, url: String): SearchPage {
        val results = doc.select(".block-body > li.block-row, ol.block-body > li").mapNotNull { li ->
            val a = li.selectFirst(".contentRow-title a") ?: return@mapNotNull null
            val minor = li.select(".contentRow-minor li").map { it.text() }
            SearchResult(
                title = a.text(),
                url = a.absUrl("href"),
                snippet = li.selectFirst(".contentRow-snippet")?.text().orEmpty(),
                author = li.attr("data-author").ifBlank { li.selectFirst(".contentRow-minor .username")?.text().orEmpty() },
                avatarUrl = li.selectFirst(".contentRow-figure img")?.absUrl("src")?.ifBlank { null },
                epoch = li.selectFirst("time[data-time]")?.attr("data-time")?.toLongOrNull() ?: 0L,
                forum = li.selectFirst(".contentRow-minor a[href*=/forums/]")?.text().orEmpty(),
                kind = minor.firstOrNull { it.startsWith("Post #") || it.startsWith("Thread") }.orEmpty(),
            )
        }.distinctBy { it.url }
        val (page, total) = pages(doc)
        if (results.isEmpty()) {
            doc.selectFirst(".blockMessage")?.text()?.takeIf { it.isNotBlank() }?.let { throw java.io.IOException(it) }
        }
        return SearchPage(results, page, total, url)
    }

    /** Page heading without thread-prefix labels. */
    private fun cleanTitle(doc: Document): String? {
        val h = doc.selectFirst("h1.p-title-value")?.clone() ?: return null
        h.select(".label, .labelLink").remove()
        return h.text().trim().ifBlank { null }
    }

    private fun pages(doc: Document): Pair<Int, Int> {
        val current = doc.selectFirst(".pageNav-page--current")?.text()?.trim()?.toIntOrNull() ?: 1
        val total = doc.select(".pageNav-main .pageNav-page").mapNotNull { it.text().trim().toIntOrNull() }
            .maxOrNull() ?: 1
        return current to maxOf(total, current)
    }

    fun forumPage(doc: Document, url: String): ForumPage {
        val threads = doc.select(".structItem--thread").mapNotNull { el ->
            val titleBox = el.selectFirst(".structItem-title") ?: return@mapNotNull null
            val link = titleBox.select("a[href*=/threads/]").lastOrNull() ?: return@mapNotNull null
            val href = link.absUrl("href")
            val metaDd = el.select(".structItem-cell--meta dd").map { it.text() }
            ThreadItem(
                id = idFromUrl(href),
                title = link.text(),
                prefix = titleBox.select(".label").joinToString(" ") { it.text() },
                url = href,
                author = el.attr("data-author").ifBlank {
                    el.selectFirst(".structItem-minor .username")?.text().orEmpty()
                },
                replies = metaDd.getOrNull(0).orEmpty(),
                views = metaDd.getOrNull(1).orEmpty(),
                lastPostEpoch = el.selectFirst(".structItem-latestDate")?.attr("data-time")?.toLongOrNull() ?: 0L,
                lastPoster = el.selectFirst(".structItem-cell--latest .username")?.text().orEmpty(),
                sticky = el.parents().any { it.hasClass("structItemContainer-group--sticky") } ||
                    el.selectFirst(".structItem-status--sticky") != null,
                locked = el.selectFirst(".structItem-status--locked") != null,
                unread = el.hasClass("is-unread"),
            )
        }.distinctBy { it.id }
        val (page, total) = pages(doc)
        return ForumPage(
            title = cleanTitle(doc) ?: "Forum",
            url = url,
            subNodes = nodeSections(doc).flatMap { it.nodes },
            threads = threads,
            page = page,
            totalPages = total,
            canPostThread = session(doc).loggedIn && doc.selectFirst("a[href*=/post-thread]") != null,
        )
    }

    fun threadPage(doc: Document, url: String): ThreadPage {
        // Thread posts and direct-message (conversation) messages share the same markup.
        val posts = doc.select("article.message--post, article.message--conversationMessage").mapNotNull { el ->
            val id = Regex("""\d+""").find(el.attr("data-content"))?.value?.toIntOrNull()
                ?: Regex("""\d+""").find(el.id())?.value?.toIntOrNull() ?: return@mapNotNull null
            val body = el.selectFirst(".message-userContent .bbWrapper")
                ?: el.selectFirst(".message-body .bbWrapper") ?: return@mapNotNull null
            val (html, images) = cleanBody(body)
            val attachments = el.select(".message-attachments a.file-preview").map { it.absUrl("href") } +
                el.select(".message-attachments img").map { it.absUrl("src") }
            Post(
                id = id,
                number = el.select(".message-attribution-opposite a").map { it.text() }
                    .firstOrNull { it.startsWith("#") }.orEmpty(),
                author = el.attr("data-author").ifBlank { el.selectFirst(".message-name")?.text().orEmpty() },
                userTitle = el.selectFirst(".message-userTitle")?.text().orEmpty(),
                avatarUrl = el.selectFirst(".message-avatar img")?.absUrl("src")?.ifBlank { null },
                epoch = el.selectFirst(".message-attribution-main time")?.attr("data-time")?.toLongOrNull() ?: 0L,
                html = html,
                images = (images + attachments).filter { it.isNotBlank() }.distinct(),
                isStaff = el.selectFirst(".message-staffBanner, .userBanner--staff") != null,
            )
        }
        val (page, total) = pages(doc)
        val replyForm = doc.selectFirst("form.js-quickReply, form[action*=/add-reply]")
        val locked = doc.select(".blockStatus").text().contains("Not open", ignoreCase = true)
        return ThreadPage(
            title = cleanTitle(doc) ?: "Thread",
            url = url,
            posts = posts,
            page = page,
            totalPages = total,
            canReply = replyForm != null,
            replyAction = replyForm?.absUrl("action")?.ifBlank { null },
            locked = locked,
        )
    }

    /** Turns a post's bbWrapper into simple HTML that Android's Html parser renders well, plus image URLs. */
    fun cleanBody(src: Element): Pair<String, List<String>> {
        val body = src.clone()
        body.select("script, style, .bbCodeBlock-expandLink, .bbCodeBlock-shrinkLink, .bbCodeSpoiler-button").remove()
        val images = mutableListOf<String>()

        body.select("img.smilie").forEach { it.replaceWith(org.jsoup.nodes.TextNode(it.attr("alt"))) }
        body.select("img").forEach { img ->
            val u = img.absUrl("data-src").ifBlank { img.absUrl("src") }
            if (u.isNotBlank() && !u.startsWith("data:")) images += u
            img.remove()
        }
        body.select("iframe, video").forEach { m ->
            val u = m.absUrl("src")
            if (m.parent() != null) m.replaceWith(Element("a").attr("href", u).text("[embedded media]"))
        }
        // Innermost first, so nested quotes are converted before their parent copies them.
        body.select("blockquote.bbCodeBlock--quote, div.bbCodeBlock--quote").toList().asReversed().forEach { q ->
            if (q.parent() == null) return@forEach
            val title = q.selectFirst(".bbCodeBlock-title")?.text().orEmpty()
            val content = q.selectFirst(".bbCodeBlock-content")?.html() ?: q.html()
            val titleHtml = if (title.isNotBlank()) "<b>${org.jsoup.nodes.Entities.escape(title)}</b><br>" else ""
            q.replaceWith(Jsoup.parseBodyFragment("<blockquote>$titleHtml$content</blockquote>").body().child(0))
        }
        body.select(".bbCodeBlock--code").toList().asReversed().forEach { c ->
            if (c.parent() == null) return@forEach
            val code = c.selectFirst("code, pre")?.wholeText() ?: c.text()
            val escaped = org.jsoup.nodes.Entities.escape(code).replace("\n", "<br>")
            c.replaceWith(Jsoup.parseBodyFragment("<blockquote><tt>$escaped</tt></blockquote>").body().child(0))
        }
        return body.html() to images
    }

    /** RSS 2.0 (XenForo forum feeds and SourceForge file feeds). */
    fun rss(xml: String, source: String): List<FeedItem> {
        val doc = Jsoup.parse(xml, "", Parser.xmlParser())
        return doc.select("item").map { item ->
            val link = item.selectFirst("link")?.text().orEmpty()
            FeedItem(
                guid = item.selectFirst("guid")?.text()?.ifBlank { null } ?: link,
                title = item.selectFirst("title")?.text().orEmpty(),
                link = link,
                epoch = parseRssDate(item.selectFirst("pubDate")?.text()),
                source = source,
                author = rssAuthor(item),
            )
        }
    }

    /** XenForo puts "invalid@example.com (username)" in <author>; prefer dc:creator, else keep the name only. */
    private fun rssAuthor(item: org.jsoup.nodes.Element): String {
        val creator = item.selectFirst("dc|creator")?.text()?.trim().orEmpty()
        if (creator.isNotBlank() && '@' !in creator) return creator
        val raw = item.selectFirst("author")?.text()?.trim().orEmpty()
        Regex("""\((.+)\)\s*$""").find(raw)?.let { return it.groupValues[1].trim() }
        return if ('@' in raw) "" else raw
    }

    private fun parseRssDate(s: String?): Long {
        if (s.isNullOrBlank()) return 0L
        val norm = s.trim().replace(Regex("""\b(UT|UTC|GMT)$"""), "+0000")
        val fmts = listOf("EEE, dd MMM yyyy HH:mm:ss Z", "EEE, dd MMM yyyy HH:mm:ss zzz", "dd MMM yyyy HH:mm:ss Z")
        for (f in fmts) {
            runCatching {
                return java.text.SimpleDateFormat(f, java.util.Locale.US).parse(norm)!!.time / 1000
            }
        }
        return 0L
    }
}
