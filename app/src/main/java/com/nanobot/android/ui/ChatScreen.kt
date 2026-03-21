package com.nanobot.android.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nanobot.android.bus.AgentState
import com.nanobot.android.model.ChatUiMessage
import com.nanobot.android.model.MessageRole
import kotlinx.coroutines.launch

/**
 * 聊天主界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = viewModel()
) {
    val messages by viewModel.messages.collectAsState()
    val inputText by viewModel.inputText.collectAsState()
    val agentStatus by viewModel.agentStatus.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // 新消息到来时自动滚动到底部
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NanoBot") },
                actions = {
                    // Agent 状态指示器
                    AgentStatusIndicator(
                        state = agentStatus.state,
                        currentAction = agentStatus.currentAction
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    // 新建会话按钮
                    IconButton(onClick = { viewModel.newSession() }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "新建会话"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            ChatInputBar(
                text = inputText,
                onTextChange = viewModel::updateInputText,
                onSend = { viewModel.sendMessage(inputText) },
                isLoading = isLoading
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (messages.isEmpty()) {
                // 空状态提示
                EmptyStateView(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(
                        items = messages,
                        key = { it.id }
                    ) { message ->
                        MessageBubble(message = message)
                    }

                    // 加载指示器
                    if (isLoading && (messages.isEmpty() || messages.last().role != MessageRole.ASSISTANT)) {
                        item {
                            ThinkingIndicator()
                        }
                    }
                }
            }
        }
    }
}

/**
 * 消息气泡
 */
@Composable
fun MessageBubble(message: ChatUiMessage) {
    val isUser = message.role == MessageRole.USER
    val isSystem = message.role == MessageRole.SYSTEM

    when {
        isSystem -> SystemMessageBubble(message.text)
        message.isThinking -> ThinkingBubble(message.thinkingText ?: "")
        isUser -> UserMessageBubble(message)
        else -> AssistantMessageBubble(message)
    }
}

/**
 * 用户消息气泡
 */
@Composable
fun UserMessageBubble(message: ChatUiMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 4.dp,
                bottomStart = 16.dp, bottomEnd = 16.dp
            ),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    text = message.text,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 15.sp
                )
                // 附件显示（如果有）
                if (message.attachments.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "📎 ${message.attachments.size} 个附件",
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

/**
 * AI 消息气泡
 */
@Composable
fun AssistantMessageBubble(message: ChatUiMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        // AI 头像
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.tertiary),
            contentAlignment = Alignment.Center
        ) {
            Text("🤖", fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.width(8.dp))

        Surface(
            shape = RoundedCornerShape(
                topStart = 4.dp, topEnd = 16.dp,
                bottomStart = 16.dp, bottomEnd = 16.dp
            ),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (message.text.isNotEmpty()) {
                    MarkdownText(
                        text = message.text,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 流式输出光标
                if (message.isStreaming) {
                    Text(
                        text = "▋",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

/**
 * 简化版 Markdown 文本渲染（纯 Compose）
 *
 * 注：完整实现应使用 Markwon 或 compose-markdown 库
 */
@Composable
fun MarkdownText(
    text: String,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    // 分段处理代码块
    val segments = parseMarkdownSegments(text)

    Column {
        segments.forEach { segment ->
            when (segment) {
                is MarkdownSegment.Code -> {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = segment.code,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
                is MarkdownSegment.Normal -> {
                    Text(
                        text = segment.text,
                        color = color,
                        fontSize = 15.sp,
                        lineHeight = 22.sp
                    )
                }
            }
        }
    }
}

sealed class MarkdownSegment {
    data class Normal(val text: String) : MarkdownSegment()
    data class Code(val code: String, val language: String = "") : MarkdownSegment()
}

fun parseMarkdownSegments(text: String): List<MarkdownSegment> {
    val segments = mutableListOf<MarkdownSegment>()
    val codeBlockPattern = Regex("```(\\w+)?\\n([\\s\\S]*?)```")

    var lastEnd = 0
    codeBlockPattern.findAll(text).forEach { match ->
        if (match.range.first > lastEnd) {
            segments.add(MarkdownSegment.Normal(text.substring(lastEnd, match.range.first)))
        }
        segments.add(MarkdownSegment.Code(
            code = match.groupValues[2],
            language = match.groupValues[1]
        ))
        lastEnd = match.range.last + 1
    }

    if (lastEnd < text.length) {
        segments.add(MarkdownSegment.Normal(text.substring(lastEnd)))
    }

    return if (segments.isEmpty()) listOf(MarkdownSegment.Normal(text)) else segments
}

/**
 * 系统消息
 */
@Composable
fun SystemMessageBubble(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            fontStyle = FontStyle.Italic,
            modifier = Modifier.padding(vertical = 4.dp)
        )
    }
}

/**
 * 思维链折叠显示
 */
@Composable
fun ThinkingBubble(thinkingText: String) {
    var expanded by remember { mutableStateOf(false) }

    Row(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.width(40.dp))

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    Text(
                        text = "💭 思考过程",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = "展开/折叠",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                AnimatedVisibility(visible = expanded) {
                    Text(
                        text = thinkingText,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontStyle = FontStyle.Italic,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

/**
 * 思考中指示器（三点动画）
 */
@Composable
fun ThinkingIndicator() {
    Row(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.width(40.dp))
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    )
                }
            }
        }
    }
}

/**
 * Agent 状态指示器
 */
@Composable
fun AgentStatusIndicator(state: AgentState, currentAction: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    when (state) {
                        AgentState.IDLE -> Color(0xFF4CAF50)
                        AgentState.THINKING -> Color(0xFF2196F3)
                        AgentState.TOOL_CALLING -> Color(0xFFFF9800)
                        AgentState.RESPONDING -> Color(0xFF9C27B0)
                        AgentState.ERROR -> Color(0xFFF44336)
                    }
                )
        )
        if (state != AgentState.IDLE && currentAction.isNotEmpty()) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = currentAction.take(20),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

/**
 * 输入栏
 */
@Composable
fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    isLoading: Boolean
) {
    Surface(
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = { Text("输入消息...") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                maxLines = 6,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(
                    onSend = { if (!isLoading) onSend() }
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            // 发送按钮
            FilledIconButton(
                onClick = onSend,
                enabled = text.isNotBlank() && !isLoading,
                modifier = Modifier.size(48.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "发送"
                    )
                }
            }
        }
    }
}

/**
 * 空状态提示
 */
@Composable
fun EmptyStateView(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("🤖", fontSize = 48.sp)
        Text(
            text = "你好！我是 NanoBot",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "有什么我可以帮助你的？",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(
                "帮我搜索最新的新闻",
                "解释一下量子计算",
                "帮我写一个 Python 脚本"
            ).forEach { suggestion ->
                SuggestionChip(
                    onClick = {},
                    label = { Text(suggestion, fontSize = 13.sp) }
                )
            }
        }
    }
}
