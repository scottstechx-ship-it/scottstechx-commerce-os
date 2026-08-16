package com.scottstechx.commerceos.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.scottstechx.commerceos.ui.brand.BrandLogo
import com.scottstechx.commerceos.ui.common.HelpDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerChatScreen(
    onBack: () -> Unit,
    viewModel: CustomerChatViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var input by remember { mutableStateOf("") }
    var showHelp by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    val keyboard = LocalSoftwareKeyboardController.current

    // Auto-scroll on new messages.
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            list.animateScrollToItem(state.messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BrandLogo(size = 32.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Shopping helper")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showHelp = true }) {
                        Icon(Icons.Filled.Help, contentDescription = "Help")
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth().imePadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Ask anything…") },
                        singleLine = false,
                        maxLines = 4
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            viewModel.send(input)
                            input = ""
                            keyboard?.hide()
                        },
                        enabled = input.isNotBlank() && !state.isSending
                    ) {
                        Icon(
                            Icons.Filled.Send,
                            contentDescription = "Send",
                            tint = if (input.isNotBlank()) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (!state.aiEnabled) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        "AI is not configured on the server. " +
                            "Chat will still send your message but won't get a reply.",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            LazyColumn(
                state = list,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.messages, key = { it.id }) { msg ->
                    ChatBubble(msg)
                }
            }
        }
    }

    if (showHelp) {
        HelpDialog(
            title = "About Shopping helper",
            body = "This is an AI shopping helper that can answer questions about " +
                "sellers near you, suggest products, or explain what a trust badge " +
                "means. It only knows what is on the marketplace — it will not invent " +
                "sellers. Be polite and ask one question at a time.",
            onDismiss = { showHelp = false }
        )
    }
}

@Composable
private fun ChatBubble(msg: ChatUiMessage) {
    val isUser = msg.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (isUser) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Text(
                if (msg.pending) "…" else msg.content,
                modifier = Modifier.padding(12.dp),
                color = if (isUser) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
