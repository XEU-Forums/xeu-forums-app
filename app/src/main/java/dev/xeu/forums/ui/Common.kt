package dev.xeu.forums.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.text.Layout
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.LeadingMarginSpan
import android.text.style.LineBackgroundSpan
import android.text.style.QuoteSpan
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.FirstPage
import androidx.compose.material.icons.filled.LastPage
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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

/** [refreshing] is true while a reload runs on top of content that's already shown. */
class Loader<T>(val state: Load<T>, val refreshing: Boolean, val reload: () -> Unit)

/** Runs [block] whenever [keys] change or reload() is called. Reloads keep the old content visible. */
@Composable
fun <T> rememberLoader(vararg keys: Any?, block: suspend () -> T): Loader<T> {
    var state by remember(*keys) { mutableStateOf<Load<T>>(Load.Loading) }
    var refreshing by remember(*keys) { mutableStateOf(false) }
    var tick by remember(*keys) { mutableIntStateOf(0) }
    LaunchedEffect(*keys, tick) {
        val hadContent = state is Load.Ok
        if (hadContent) refreshing = true else state = Load.Loading
        state = try { Load.Ok(block()) } catch (e: Exception) {
            if (hadContent) state else Load.Err(friendlyError(e))
        }
        refreshing = false
    }
    return Loader(state, refreshing) { tick++ }
}

/**
 * Like [rememberLoader], but starts from [cached] (if any) so content shows immediately,
 * then refreshes in the background.
 */
@Composable
fun <T> rememberCachedLoader(key: Any?, cached: () -> T?, block: suspend () -> T): Loader<T> {
    var state by remember(key) { mutableStateOf<Load<T>>(cached()?.let { Load.Ok(it) } ?: Load.Loading) }
    var refreshing by remember(key) { mutableStateOf(false) }
    var tick by remember(key) { mutableIntStateOf(0) }
    LaunchedEffect(key, tick) {
        val hadContent = state is Load.Ok
        if (hadContent) refreshing = true else state = Load.Loading
        state = try { Load.Ok(block()) } catch (e: Exception) {
            if (hadContent) state else Load.Err(friendlyError(e))
        }
        refreshing = false
    }
    return Loader(state, refreshing) { tick++ }
}

fun friendlyError(e: Throwable): String = when {
    e is java.net.UnknownHostException -> "No connection. Check your internet and try again."
    e is java.net.SocketTimeoutException -> "The forum is taking too long to respond. Try again."
    e is IOException && e.message?.contains("HTTP 403") == true ->
        "The forum refused the request (403). It may be blocking automated access right now."
    e is IOException && e.message?.contains("HTTP 404") == true -> "That page doesn't exist anymore."
    e is IOException -> e.message ?: "Network error."
    else -> e.message ?: e.javaClass.simpleName
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(strokeWidth = 3.dp)
    }
}

@Composable
fun ErrorBox(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier, actionLabel: String = "Try again") {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(72.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.CloudOff, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Text(
            message,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp),
        )
        FilledTonalButton(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) { Text(actionLabel) }
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

fun isForumUrl(url: String) = Regex("""^https?://(www\.)?xiaomi\.eu/community/""").containsMatchIn(url)

/** Quote block: orange bar on the left and a soft tinted background. */
private class QuoteBlockSpan(
    private val barColor: Int,
    private val bgColor: Int,
    private val barWidth: Int,
    private val gap: Int,
) : LeadingMarginSpan, LineBackgroundSpan {
    override fun getLeadingMargin(first: Boolean) = barWidth + gap

    override fun drawLeadingMargin(
        c: Canvas, p: Paint, x: Int, dir: Int, top: Int, baseline: Int, bottom: Int,
        text: CharSequence, start: Int, end: Int, first: Boolean, layout: Layout,
    ) {
        val style = p.style; val color = p.color
        p.style = Paint.Style.FILL; p.color = barColor
        c.drawRect(x.toFloat(), top.toFloat(), (x + dir * barWidth).toFloat(), bottom.toFloat(), p)
        p.style = style; p.color = color
    }

    override fun drawBackground(
        c: Canvas, p: Paint, left: Int, right: Int, top: Int, baseline: Int, bottom: Int,
        text: CharSequence, start: Int, end: Int, lineNumber: Int,
    ) {
        val color = p.color
        p.color = bgColor
        c.drawRect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat(), p)
        p.color = color
    }
}

/** Renders forum post HTML with clickable links routed through [onLink] and styled quotes. */
@Composable
fun HtmlText(html: String, onLink: (String) -> Unit, modifier: Modifier = Modifier) {
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()
    val quoteBar = MaterialTheme.colorScheme.primary.toArgb()
    val quoteBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.07f).toArgb()
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { ctx ->
            TextView(ctx).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f)
                setLineSpacing(0f, 1.2f)
                movementMethod = LinkMovementMethod.getInstance()
                setTextIsSelectable(false)
            }
        },
        update = { tv ->
            tv.setTextColor(textColor)
            tv.setLinkTextColor(linkColor)
            tv.highlightColor = quoteBg
            val density = tv.resources.displayMetrics.density
            val spanned = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT)
            val sb = SpannableStringBuilder(spanned)
            while (sb.isNotEmpty() && sb.last().isWhitespace()) sb.delete(sb.length - 1, sb.length)
            sb.getSpans(0, sb.length, QuoteSpan::class.java).forEach { q ->
                val s = sb.getSpanStart(q); val e = sb.getSpanEnd(q); val f = sb.getSpanFlags(q)
                sb.removeSpan(q)
                sb.setSpan(QuoteBlockSpan(quoteBar, quoteBg, (3 * density).toInt(), (12 * density).toInt()), s, e, f)
            }
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

/** Floating page switcher. */
@Composable
fun PagerBar(page: Int, total: Int, onPage: (Int) -> Unit, modifier: Modifier = Modifier) {
    if (total <= 1) return
    Box(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 2.dp,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                IconButton(onClick = { onPage(1) }, enabled = page > 1) { Icon(Icons.Filled.FirstPage, "First page") }
                IconButton(onClick = { onPage(page - 1) }, enabled = page > 1) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous page")
                }
                Text(
                    "$page / $total",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                IconButton(onClick = { onPage(page + 1) }, enabled = page < total) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next page")
                }
                IconButton(onClick = { onPage(total) }, enabled = page < total) { Icon(Icons.Filled.LastPage, "Last page") }
            }
        }
    }
}
