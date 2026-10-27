package dev.xeu.reader.ui

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
import dev.xeu.reader.Nav
import dev.xeu.reader.data.ForumRepository
import dev.xeu.reader.data.Http
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
            if (sending) CircularProgressIndicator(Modifier.size(24.dp).padding(end = 4.dp))
            else IconButton(onClick = { confirm = true }, enabled = canSend) {
                Icon(Icons.AutoMirrored.Filled.Send, "Post")
            }
        },
    ) { mod ->
        if (!Http.isLoggedIn()) {
            Column(mod.padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Log in to your xiaomi.eu account to post.")
                Button(onClick = { nav.login() }, modifier = Modifier.padding(top = 16.dp)) { Text("Log in") }
            }
            return@ScreenScaffold
        }
        Column(mod.imePadding().padding(16.dp)) {
            if (isThread) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(false, { wrap("[B]", "[/B]") }, { Text("Bold") })
                FilterChip(false, { wrap("[I]", "[/I]") }, { Text("Italic") })
                FilterChip(false, { wrap("[URL]", "[/URL]") }, { Text("Link") })
                FilterChip(false, { wrap("[QUOTE]", "[/QUOTE]") }, { Text("Quote") })
                FilterChip(false, { wrap("[CODE]", "[/CODE]") }, { Text("Code") })
                FilterChip(false, { wrap("[SPOILER]", "[/SPOILER]") }, { Text("Spoiler") })
            }
            OutlinedTextField(
                value = body, onValueChange = { body = it },
                label = { Text("Message (BB code)") },
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
            text = { Text("It will be published publicly on xiaomi.eu under your account.") },
            confirmButton = { TextButton(onClick = { confirm = false; send() }) { Text("Post") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}
