package com.nuvio.app.features.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.NuvioInputField
import com.nuvio.app.core.ui.NuvioModalBottomSheet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.ai_assistant_disclaimer
import nuvio.composeapp.generated.resources.ai_assistant_error
import nuvio.composeapp.generated.resources.ai_assistant_input_hint
import nuvio.composeapp.generated.resources.ai_assistant_send
import nuvio.composeapp.generated.resources.ai_assistant_title
import org.jetbrains.compose.resources.stringResource

/** Avix (Yesnt10 port): chat about the current title with the user's own AI provider. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantSheet(title: AiTitleContext, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var messages by remember(title) { mutableStateOf<List<AiChatMessage>>(emptyList()) }
    var input by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val genericError = stringResource(Res.string.ai_assistant_error)

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    fun send() {
        val text = input.trim()
        if (text.isEmpty() || loading) return
        val conversation = messages + AiChatMessage(AiChatRole.USER, text)
        messages = conversation
        input = ""
        loading = true
        errorText = null
        scope.launch {
            try {
                val reply = AiAssistantService.chat(title, conversation)
                messages = conversation + AiChatMessage(AiChatRole.ASSISTANT, reply.answer, reply.sources)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Throwable) {
                errorText = (error as? AiAssistantException)?.message
                    ?.takeUnless { it == "not_configured" || it == "empty" || it == "empty_response" }
                    ?.let { "$genericError ($it)" }
                    ?: genericError
            } finally {
                loading = false
            }
        }
    }

    NuvioModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(Res.string.ai_assistant_title, title.name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(Res.string.ai_assistant_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(messages) { message -> AiMessageBubble(message) }
                if (loading) {
                    item { CircularProgressIndicator(modifier = Modifier.padding(8.dp)) }
                }
            }
            errorText?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                NuvioInputField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = stringResource(Res.string.ai_assistant_input_hint),
                    modifier = Modifier.weight(1f),
                    trailingContent = {
                        TextButton(onClick = ::send, enabled = !loading && input.isNotBlank()) {
                            Text(stringResource(Res.string.ai_assistant_send))
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun AiMessageBubble(message: AiChatMessage) {
    val isUser = message.role == AiChatRole.USER
    val uriHandler = LocalUriHandler.current
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .background(
                    color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(14.dp),
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            message.sources.forEachIndexed { index, source ->
                TextButton(onClick = { runCatching { uriHandler.openUri(source.url) } }) {
                    Text(
                        text = "[${index + 1}] ${source.title}",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
