package com.nanobot.android.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nanobot.android.NanoBotApplication
import com.nanobot.android.bus.AgentState
import com.nanobot.android.bus.AgentStatus
import com.nanobot.android.bus.MessageBus
import com.nanobot.android.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * ChatViewModel - 聊天界面的 ViewModel
 *
 * 管理 UI 状态，桥接 MessageBus 和 Compose UI
 */
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val app get() = getApplication<NanoBotApplication>()

    // ==================== UI 状态 ====================

    private val _messages = MutableStateFlow<List<ChatUiMessage>>(emptyList())
    val messages: StateFlow<List<ChatUiMessage>> = _messages.asStateFlow()

    private val _agentStatus = MutableStateFlow(AgentStatus(AgentState.IDLE))
    val agentStatus: StateFlow<AgentStatus> = _agentStatus.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _currentSessionKey = MutableStateFlow("default")
    val currentSessionKey: StateFlow<String> = _currentSessionKey.asStateFlow()

    // 会话列表（侧边栏用）
    private val _sessionList = MutableStateFlow<List<ConversationItem>>(emptyList())
    val sessionList: StateFlow<List<ConversationItem>> = _sessionList.asStateFlow()

    // 日志面板是否展开
    private val _showLogPanel = MutableStateFlow(false)
    val showLogPanel: StateFlow<Boolean> = _showLogPanel.asStateFlow()

    // 当前正在流式输出的消息 ID
    private var streamingMessageId: String? = null

    val chatId: String = "main"

    init {
        observeOutboundMessages()
        observeAgentStatus()
        refreshSessionList()
    }

    /**
     * 监听来自 Agent 的出站消息
     */
    private fun observeOutboundMessages() {
        viewModelScope.launch {
            MessageBus.outboundFlow.collect { outbound ->
                if (outbound.chatId != chatId) return@collect
                handleOutboundMessage(outbound)
            }
        }
    }

    /**
     * 监听 Agent 状态
     */
    private fun observeAgentStatus() {
        viewModelScope.launch {
            MessageBus.agentStatusFlow.collect { status ->
                _agentStatus.value = status
                _isLoading.value = status.state != AgentState.IDLE && status.state != AgentState.ERROR
                // 每次 Agent 回到 IDLE 时刷新会话列表
                if (status.state == AgentState.IDLE) {
                    refreshSessionList()
                }
            }
        }
    }

    /**
     * 处理出站消息，更新 UI
     */
    private fun handleOutboundMessage(outbound: OutboundMessage) {
        val messageType = outbound.metadata["type"] ?: "normal"

        when {
            outbound.isError -> {
                // 错误消息
                addMessage(ChatUiMessage(
                    id = UUID.randomUUID().toString(),
                    role = MessageRole.ASSISTANT,
                    text = "❌ ${outbound.text}",
                    error = outbound.text
                ))
                streamingMessageId = null
            }
            messageType == "thinking" -> {
                // 思维链内容（折叠显示）
                addMessage(ChatUiMessage(
                    id = UUID.randomUUID().toString(),
                    role = MessageRole.ASSISTANT,
                    text = "",
                    isThinking = true,
                    thinkingText = outbound.text
                ))
            }
            messageType == "tool_notify" -> {
                // 工具调用通知（轻量显示）
                addMessage(ChatUiMessage(
                    id = UUID.randomUUID().toString(),
                    role = MessageRole.ASSISTANT,
                    text = outbound.text,
                    metadata = mapOf("type" to "tool_notify")
                ))
            }
            messageType == "system" -> {
                // 系统消息
                addMessage(ChatUiMessage(
                    id = UUID.randomUUID().toString(),
                    role = MessageRole.SYSTEM,
                    text = outbound.text
                ))
            }
            outbound.isComplete -> {
                // 完整的最终响应
                if (streamingMessageId != null) {
                    // 更新流式消息为完整版
                    updateStreamingMessage(outbound.text, isComplete = true)
                    streamingMessageId = null
                } else {
                    addMessage(ChatUiMessage(
                        id = UUID.randomUUID().toString(),
                        role = MessageRole.ASSISTANT,
                        text = outbound.text
                    ))
                }
            }
            outbound.isStreaming -> {
                // 流式输出中
                if (streamingMessageId == null) {
                    val msgId = UUID.randomUUID().toString()
                    streamingMessageId = msgId
                    addMessage(ChatUiMessage(
                        id = msgId,
                        role = MessageRole.ASSISTANT,
                        text = outbound.text,
                        isStreaming = true
                    ))
                } else {
                    appendToStreamingMessage(outbound.text)
                }
            }
        }
    }

    private fun addMessage(message: ChatUiMessage) {
        _messages.update { current -> current + message }
    }

    private fun updateStreamingMessage(finalText: String, isComplete: Boolean) {
        val id = streamingMessageId ?: return
        _messages.update { current ->
            current.map { msg ->
                if (msg.id == id) {
                    msg.copy(text = finalText, isStreaming = !isComplete)
                } else msg
            }
        }
    }

    private fun appendToStreamingMessage(delta: String) {
        val id = streamingMessageId ?: return
        _messages.update { current ->
            current.map { msg ->
                if (msg.id == id) {
                    msg.copy(text = msg.text + delta)
                } else msg
            }
        }
    }

    // ==================== 用户操作 ====================

    /**
     * 发送消息
     */
    fun sendMessage(text: String, attachments: List<Attachment> = emptyList()) {
        if (text.isBlank() && attachments.isEmpty()) return

        val trimmedText = text.trim()

        // 添加用户消息到 UI
        addMessage(ChatUiMessage(
            id = UUID.randomUUID().toString(),
            role = MessageRole.USER,
            text = trimmedText,
            attachments = attachments
        ))

        // 清空输入框
        _inputText.value = ""

        // 发送到 Agent
        val inbound = InboundMessage(
            chatId = chatId,
            text = trimmedText,
            attachments = attachments,
            sessionKeyOverride = _currentSessionKey.value
        )
        MessageBus.publishInbound(inbound)
    }

    fun updateInputText(text: String) {
        _inputText.value = text
    }

    /**
     * 清空聊天记录（UI 层）
     */
    fun clearChat() {
        _messages.value = emptyList()
        streamingMessageId = null
    }

    /**
     * 开始新会话
     */
    fun newSession() {
        clearChat()
        val newKey = UUID.randomUUID().toString().take(8)
        _currentSessionKey.value = newKey

        // 通过 /new 命令通知 Agent
        val inbound = InboundMessage(
            chatId = chatId,
            text = "/new",
            sessionKeyOverride = newKey,
            isSystemCommand = true
        )
        MessageBus.publishInbound(inbound)
        refreshSessionList()
    }

    /**
     * 切换到已有会话
     */
    fun switchSession(sessionKey: String) {
        clearChat()
        _currentSessionKey.value = sessionKey

        // 加载该会话的 UI 消息（从 SessionManager 读取历史）
        viewModelScope.launch {
            try {
                val messages = app.sessionManager.getMessages(sessionKey)
                val uiMessages = messages.mapNotNull { msg ->
                    when (msg.role) {
                        "user" -> ChatUiMessage(
                            id = UUID.randomUUID().toString(),
                            role = MessageRole.USER,
                            text = msg.content ?: ""
                        )
                        "assistant" -> if ((msg.content ?: "").isNotBlank()) ChatUiMessage(
                            id = UUID.randomUUID().toString(),
                            role = MessageRole.ASSISTANT,
                            text = msg.content ?: ""
                        ) else null
                        else -> null
                    }
                }
                _messages.value = uiMessages
            } catch (e: Exception) {
                // 读取失败时保持空会话
            }
        }
    }

    /**
     * 删除会话
     */
    fun deleteSession(sessionKey: String) {
        app.sessionManager.deleteSession(sessionKey)
        if (sessionKey == _currentSessionKey.value) {
            newSession()
        } else {
            refreshSessionList()
        }
    }

    /**
     * 刷新会话列表
     */
    fun refreshSessionList() {
        viewModelScope.launch {
            try {
                val keys = app.sessionManager.listSessions()
                val items = keys.map { key ->
                    val messages = app.sessionManager.getMessages(key)
                    val lastUserMsg = messages.lastOrNull { it.role == "user" }?.content ?: ""
                    val firstUserMsg = messages.firstOrNull { it.role == "user" }?.content ?: key
                    ConversationItem(
                        sessionKey = key,
                        title = firstUserMsg.take(20).ifEmpty { "会话 ${key.take(4)}" },
                        lastMessage = lastUserMsg.take(40).ifEmpty { "（空会话）" },
                        messageCount = messages.size,
                        updatedAt = messages.lastOrNull()?.timestamp ?: 0L
                    )
                }.sortedByDescending { it.updatedAt }
                _sessionList.value = items
            } catch (e: Exception) {
                _sessionList.value = emptyList()
            }
        }
    }

    // ==================== 日志面板 ====================

    fun toggleLogPanel() {
        _showLogPanel.value = !_showLogPanel.value
    }

    fun hideLogPanel() {
        _showLogPanel.value = false
    }
}

