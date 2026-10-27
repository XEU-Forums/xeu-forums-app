package dev.xeu.community.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import java.io.IOException

sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ok<T>(val value: T) : Load<T>
    data class Err(val message: String) : Load<Nothing>
}

class Loader<T>(val state: Load<T>, val reload: () -> Unit)

/** Runs [block] whenever [keys] change or reload() is called. */
@Composable
fun <T> rememberLoader(vararg keys: Any?, block: suspend () -> T): Loader<T> {
    var state by remember(*keys) { mutableStateOf<Load<T>>(Load.Loading) }
    var tick by remember(*keys) { mutableIntStateOf(0) }
    LaunchedEffect(*keys, tick) {
        state = Load.Loading
        state = try { Load.Ok(block()) } catch (e: Exception) { Load.Err(friendlyError(e)) }
    }
    return Loader(state) { tick++ }
}

fun friendlyError(e: Throwable): String = when {
    e is java.net.UnknownHostException -> "No connection. Check your internet and try again."
    e is IOException && e.message?.contains("HTTP 403") == true ->
        "The forum refused the request (403). It may be blocking automated access right now."
    e is IOException && e.message?.contains("HTTP 404") == true -> "That page doesn't exist anymore."
    e is IOException -> e.message ?: "Network error."
    else -> e.message ?: e.javaClass.simpleName
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun ErrorBox(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) { Text("Try again") }
    }
}

fun relativeTime(epochSeconds: Long): String {
    if (epochSeconds <= 0) return ""
    val diff = System.currentTimeMillis() / 1000 - epochSeconds
    return when {
        diff < 60 -> "just now"
        diff < 3600 -> "${diff / 60}m ago"
        diff < 86400 -> "${diff / 3600}h ago"
        diff < 86400 * 7 -> "${diff / 86400}d ago"
        else -> java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM)
            .format(java.util.Date(epochSeconds * 1000))
    }
}

fun openExternal(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

fun isForumUrl(url: String) = Regex("""^https?://(www\.)?xiaomi\.eu/community/""").containsMatchIn(url)

/** Renders forum post HTML with clickable links that are routed through [onLink]. */
@Composable
fun HtmlText(html: String, onLink: (String) -> Unit, modifier: Modifier = Modifier) {
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { ctx ->
            TextView(ctx).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setLineSpacing(0f, 1.15f)
                movementMethod = LinkMovementMethod.getInstance()
                setTextIsSelectable(false)
            }
        },
        update = { tv ->
            tv.setTextColor(textColor)
            tv.setLinkTextColor(linkColor)
            val spanned = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT)
            val sb = SpannableStringBuilder(spanned)
            // Trim trailing whitespace Html adds after block elements.
            while (sb.isNotEmpty() && sb.last().isWhitespace()) sb.delete(sb.length - 1, sb.length)
            sb.getSpans(0, sb.length, URLSpan::class.java).forEach { span ->
                val start = sb.getSpanStart(span); val end = sb.getSpanEnd(span); val url = span.url
                sb.removeSpan(span)
                sb.setSpan(object : ClickableSpan() {
                    override fun onClick(widget: View) = onLink(url)
                }, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            tv.text = sb
        },
    )
}

@Composable
fun PagerBar(page: Int, total: Int, onPage: (Int) -> Unit) {
    if (total <= 1) return
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = { onPage(1) }, enabled = page > 1) { Text("«") }
        IconButton(onClick = { onPage(page - 1) }, enabled = page > 1) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous page")
        }
        Text("Page $page of $total", style = MaterialTheme.typography.labelLarge)
        IconButton(onClick = { onPage(page + 1) }, enabled = page < total) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next page")
        }
        IconButton(onClick = { onPage(total) }, enabled = page < total) { Text("»") }
    }
}
