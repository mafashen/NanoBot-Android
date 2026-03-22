package com.nanobot.android.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// ==================== 消息模型 ====================

/**
 * 消息角色
 */
enum class MessageRole(val value: String) {
    SYSTEM("system"),
    USER("user"),
    ASSISTANT("assistant"),
    TOOL("tool")
}

/**
 * 工具调用请求
 */
@Serializable
data class ToolCallRequest(
    val id: String = "",
    val name: String,
    val arguments: String = "{}"  // JSON 字符串
)

/**
 * 消息内容块（支持文本和图片）
 */
@Serializable
sealed class ContentBlock {
    @Serializable
    @SerialName("text")
    data class Text(val text: String) : ContentBlock()

    @Serializable
    @SerialName("image")
    data class Image(
        val mimeType: String,
        val data: String  // base64 编码
    ) : ContentBlock()

    @Serializable
    @SerialName("tool_use")
    data class ToolUse(
        val id: String,
        val name: String,
        val input: String  // JSON 字符串
    ) : ContentBlock()

    @Serializable
    @SerialName("tool_result")
    data class ToolResult(
        val toolUseId: String,
        val content: String,
        val isError: Boolean = false
    ) : ContentBlock()

    @Serializable
    @SerialName("thinking")
    data class Thinking(val thinking: String) : ContentBlock()
}

/**
 * 聊天消息（用于 LLM API 调用）
 */
@Serializable
data class ChatMessage(
    val role: String,
    val content: String? = null,
    val contentBlocks: List<ContentBlock>? = null,
    val toolCalls: List<ToolCallRequest>? = null,
    val toolCallId: String? = null,
    val reasoningContent: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 入站消息（从 UI 接收）
 */
data class InboundMessage(
    val channel: String = "local",
    val senderId: String = "user",
    val chatId: String,
    val text: String,
    val attachments: List<Attachment> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
    val sessionKeyOverride: String? = null,
    val isSystemCommand: Boolean = false
)

/**
 * 出站消息（发送给 UI）
 */
data class OutboundMessage(
    val channel: String = "local",
    val chatId: String,
    val text: String,
    val attachments: List<Attachment> = emptyList(),
    val isStreaming: Boolean = false,
    val isComplete: Boolean = true,
    val isError: Boolean = false,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * 附件（图片、文件等）
 */
@Serializable
data class Attachment(
    val type: AttachmentType,
    val uri: String,
    val mimeType: String = "",
    val fileName: String = "",
    val size: Long = 0L,
    val base64Data: String? = null
)

enum class AttachmentType {
    IMAGE, AUDIO, VIDEO, FILE, DOCUMENT
}

// ==================== 会话模型 ====================

/**
 * 会话（对应 NanoBot 的 Session）
 */
@Serializable
data class Session(
    val key: String,
    val messages: MutableList<ChatMessage> = mutableListOf(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastConsolidatedIndex: Int = 0,
    val metadata: Map<String, String> = emptyMap()
)

// ==================== LLM 响应模型 ====================

/**
 * LLM 响应
 */
data class LLMResponse(
    val content: String? = null,
    val contentBlocks: List<ContentBlock> = emptyList(),
    val toolCalls: List<ToolCallRequest> = emptyList(),
    val finishReason: String = "stop",
    val usage: UsageInfo? = null,
    val reasoningContent: String? = null,
    val thinkingBlocks: List<String> = emptyList()
) {
    val hasToolCalls: Boolean get() = toolCalls.isNotEmpty()
    val isComplete: Boolean get() = finishReason in listOf("stop", "end_turn", "length")
}

/**
 * Token 使用信息
 */
@Serializable
data class UsageInfo(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0,
    val cacheCreationTokens: Int = 0,
    val cacheReadTokens: Int = 0
)

// ==================== 工具模型 ====================

/**
 * 工具参数属性
 */
@Serializable
data class ToolParameterProperty(
    val type: String,
    val description: String = "",
    val enum: List<String>? = null,
    val items: Map<String, String>? = null
)

/**
 * 工具参数
 */
@Serializable
data class ToolParameters(
    val type: String = "object",
    val properties: Map<String, ToolParameterProperty>,
    val required: List<String> = emptyList()
)

/**
 * 工具定义（发送给 LLM）
 */
@Serializable
data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: ToolParameters
)

/**
 * 工具执行结果
 */
data class ToolResult(
    val toolCallId: String,
    val toolName: String,
    val success: Boolean,
    val output: String,
    val error: String? = null
)

// ==================== 配置模型 ====================

/**
 * 应用总配置（对应 NanoBot 的 Config）
 */
@Serializable
data class AppConfig(
    val agents: AgentsConfig = AgentsConfig(),
    val providers: ProvidersConfig = ProvidersConfig(),
    val tools: ToolsConfig = ToolsConfig(),
    val ui: UIConfig = UIConfig(),
    val server: ServerConfig = ServerConfig()
)

/**
 * Agent 配置
 */
@Serializable
data class AgentsConfig(
    val defaultModel: String = "openrouter/anthropic/claude-3.5-sonnet",
    val maxIterations: Int = 40,
    val systemPromptExtra: String = "",
    val workspacePath: String = "",
    val memoryEnabled: Boolean = true,
    val consolidationThreshold: Int = 50,
    val heartbeatEnabled: Boolean = false,
    val heartbeatIntervalMinutes: Int = 60
)

/**
 * Provider 配置
 */
@Serializable
data class ProvidersConfig(
    val activeProvider: String = "openrouter",
    val providers: Map<String, ProviderConfig> = emptyMap()
)

/**
 * 单个 Provider 配置
 */
@Serializable
data class ProviderConfig(
    val name: String,
    val apiKey: String = "",
    val baseUrl: String = "",
    val defaultModel: String = "",
    val enabled: Boolean = true,
    val extraHeaders: Map<String, String> = emptyMap()
)

/**
 * 工具配置
 */
@Serializable
data class ToolsConfig(
    val fileReadEnabled: Boolean = true,
    val fileWriteEnabled: Boolean = false,
    val webSearchEnabled: Boolean = true,
    val webFetchEnabled: Boolean = true,
    val shellEnabled: Boolean = false,
    val allowedPaths: List<String> = emptyList()
)

/**
 * UI 配置
 */
@Serializable
data class UIConfig(
    val theme: String = "system",  // "light", "dark", "system"
    val fontSize: Int = 14,
    val bubbleStyle: String = "default",
    val showTimestamps: Boolean = true,
    val showTokenUsage: Boolean = false
)

/**
 * 服务器配置（用于远程 NanoBot 服务端）
 */
@Serializable
data class ServerConfig(
    val enabled: Boolean = false,
    val serverUrl: String = "",
    val apiKey: String = "",
    val useWebSocket: Boolean = true,
    val timeoutSeconds: Int = 120
)

// ==================== 聊天 UI 模型 ====================

/**
 * UI 显示用的消息条目
 */
data class ChatUiMessage(
    val id: String,
    val role: MessageRole,
    val text: String,
    val attachments: List<Attachment> = emptyList(),
    val toolCalls: List<ToolCallRequest> = emptyList(),
    val isStreaming: Boolean = false,
    val isThinking: Boolean = false,
    val thinkingText: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val error: String? = null,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * 对话历史条目（用于 UI 会话列表）
 */
data class ConversationItem(
    val sessionKey: String,
    val title: String,
    val lastMessage: String,
    val messageCount: Int,
    val updatedAt: Long
)
