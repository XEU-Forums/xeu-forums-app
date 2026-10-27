package dev.xeu.forums.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import dev.xeu.forums.Nav
import dev.xeu.forums.data.ForumRepository
import dev.xeu.forums.data.Http
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatUnderlined
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.foundation.layout.Box
import kotlinx.coroutines.launch

@Composable
fun ComposeScreen(mode: String, targetUrl: String, initialQuote: String, nav: Nav) {
    val isThread = mode == "thread"
    val scope = rememberCoroutineScope()
    var title by rememberSaveable { mutableStateOf("") }
    var body by remember { mutableStateOf(TextFieldValue(initialQuote, TextRange(initialQuote.length))) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }

    fun wrap(open: String, close: String) {
        val sel = body.selection
        val t = body.text
        val newText = t.substring(0, sel.min) + open + t.substring(sel.min, sel.max) + close + t.substring(sel.max)
        body = TextFieldValue(newText, TextRange(sel.min + open.length, sel.max + open.length))
    }

    fun send() {
        sending = true; error = null
        scope.launch {
            try {
                val redirect = if (isThread) ForumRepository.postThread(targetUrl, title.trim(), body.text.trim())
                    else ForumRepository.postReply(targetUrl, body.text.trim())
                val result = redirect.ifBlank { ForumRepository.baseUrl(targetUrl) + if (isThread) "" else "latest" }
                nav.afterPost(result, replacingThread = !isThread)
            } catch (e: Exception) {
                error = friendlyError(e)
            } finally {
                sending = false
            }
        }
    }

    val canSend = !sending && body.text.isNotBlank() && (!isThread || title.isNotBlank())

    ScreenScaffold(
        title = if (isThread) "New thread" else "Reply",
        onBack = { nav.back() },
        actions = {
            if (sending) CircularProgressIndicator(Modifier.size(24.dp).padding(end = 12.dp))
            else Button(onClick = { confirm = true }, enabled = canSend, modifier = Modifier.padding(end = 8.dp)) {
                Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(18.dp))
                androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
                Text("Post")
            }
        },
    ) { mod ->
        if (!Http.isLoggedIn()) {
            Box(mod) {
                EmptyState(androidx.compose.material.icons.Icons.Outlined.Lock, "Log in to post",
                    "Log in to your Xiaomi.EU account to reply and start threads.") {
                    Button(onClick = { nav.login() }, modifier = Modifier.padding(top = 8.dp)) { Text("Log in") }
                }
            }
            return@ScreenScaffold
        }
        Column(mod.imePadding().padding(16.dp)) {
            if (isThread) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("Thread title") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                )
            }
            androidx.compose.material3.Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.padding(vertical = 10.dp),
            ) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp)) {
                    val tools = listOf(
                        androidx.compose.material.icons.Icons.Outlined.FormatBold to ("[B]" to "[/B]"),
                        androidx.compose.material.icons.Icons.Outlined.FormatItalic to ("[I]" to "[/I]"),
                        androidx.compose.material.icons.Icons.Outlined.FormatUnderlined to ("[U]" to "[/U]"),
                        androidx.compose.material.icons.Icons.Outlined.Link to ("[URL]" to "[/URL]"),
                        androidx.compose.material.icons.Icons.Outlined.FormatQuote to ("[QUOTE]" to "[/QUOTE]"),
                        androidx.compose.material.icons.Icons.Outlined.Code to ("[CODE]" to "[/CODE]"),
                        androidx.compose.material.icons.Icons.Outlined.VisibilityOff to ("[SPOILER]" to "[/SPOILER]"),
                        androidx.compose.material.icons.Icons.Outlined.Image to ("[IMG]" to "[/IMG]"),
                    )
                    tools.forEach { (icon, tags) ->
                        IconButton(onClick = { wrap(tags.first, tags.second) }) { Icon(icon, tags.first) }
                    }
                }
            }
            OutlinedTextField(
                value = body, onValueChange = { body = it },
                placeholder = { Text("Write your message… BB code is supported") },
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
            }
            Text(
                "Forum rules apply: English only, and don't ask for ROM ETAs in release threads.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(if (isThread) "Post this thread?" else "Post this reply?") },
            text = { Text("It will be published publicly on Xiaomi.EU under your account.") },
            confirmButton = { TextButton(onClick = { confirm = false; send() }) { Text("Post") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}
