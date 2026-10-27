package dev.xeu.reader.data

/** A forum, category or link node from the forum list. */
data class Node(
    val id: Int,
    val title: String,
    val url: String,
    val description: String = "",
    val threads: String = "",
    val messages: String = "",
    val isCategory: Boolean = false,
    val hasNew: Boolean = false,
    val subNodes: List<Node> = emptyList(),
) {
    /** Device forums list their codenames in the description, e.g. "[houji, shennong, aurora]". */
    val codenames: List<String>
        get() = Regex("""^\[(.+)]$""").find(description.trim())?.groupValues?.get(1)
            ?.split(',')?.map { it.trim().lowercase() }?.filter { it.isNotEmpty() } ?: emptyList()
}

data class NodeSection(val title: String, val nodes: List<Node>)

data class ThreadItem(
    val id: Int,
    val title: String,
    val prefix: String,
    val url: String,
    val author: String,
    val replies: String,
    val views: String,
    val lastPostEpoch: Long,
    val lastPoster: String,
    val sticky: Boolean,
    val locked: Boolean,
    val unread: Boolean,
)

data class ForumPage(
    val title: String,
    val url: String,
    val subNodes: List<Node>,
    val threads: List<ThreadItem>,
    val page: Int,
    val totalPages: Int,
    val canPostThread: Boolean,
)

data class Post(
    val id: Int,
    val number: String,
    val author: String,
    val userTitle: String,
    val avatarUrl: String?,
    val epoch: Long,
    val html: String,
    val images: List<String>,
    val isStaff: Boolean,
)

data class ThreadPage(
    val title: String,
    val url: String,
    val posts: List<Post>,
    val page: Int,
    val totalPages: Int,
    val canReply: Boolean,
    val replyAction: String?,
    val locked: Boolean,
)

data class FeedItem(
    val guid: String,
    val title: String,
    val link: String,
    val epoch: Long,
    val source: String,
    val author: String = "",
)

/** A device the user follows: the forum it lives in plus its codenames. */
data class Device(val forumId: Int, val name: String, val url: String, val codenames: List<String>)

data class SessionInfo(val loggedIn: Boolean, val username: String?)
