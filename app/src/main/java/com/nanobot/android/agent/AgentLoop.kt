package com.nanobot.android.agent

import android.util.Log
import com.nanobot.android.bus.AgentState
import com.nanobot.android.bus.AgentStatus
import com.nanobot.android.bus.MessageBus
import com.nanobot.android.model.*
import com.nanobot.android.provider.LLMProvider
import com.nanobot.android.provider.ProviderRegistry
import com.nanobot.android.session.SessionManager
import com.nanobot.android.tools.ToolExecutor
import com.nanobot.android.tools.ToolRegistry
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.util.UUID

private const val TAG = "AgentLoop"
private const val MAX_ITERATIONS = 40

/**
 * AgentLoop - 核心处理引擎（Kotlin 协程版）
 *
 * 对应 NanoBot 的 agent/loop.py
 *
 * 功能：
 * 1. 持续监听消息总线中的入站消息
 * 2. 构建 LLM 上下文（系统提示词 + 历史 + 记忆）
 * 3. 循环调用 LLM，处理工具调用（最多 40 次迭代）
 * 4. 将响应发送给 UI
 * 5. 管理会话持久化和记忆整合
 */
class AgentLoop(
    private val providerRegistry: ProviderRegistry,
    private val sessionManager: SessionManager,
    private val toolRegistry: ToolRegistry,
    private val toolExecutor: ToolExecutor,
    private val contextBuilder: ContextBuilder,
    private val memoryStore: MemoryStore,
    private val config: AgentsConfig = AgentsConfig()
) {

    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // 当前活跃会话键
    private var currentSessionKey: String = "default"

    // 是否正在处理消息（防止并发）
    @Volatile
    private var isProcessing = false

    /**
     * 启动 Agent Loop（在后台协程中运行）
     */
    fun start() {
        Log.i(TAG, "AgentLoop starting...")
        job = scope.launch {
            run()
        }
    }

    /**
     * 停止 Agent Loop
     */
    fun stop() {
        Log.i(TAG, "AgentLoop stopping...")
        job?.cancel()
        scope.cancel()
    }

    /**
     * 主循环 - 持续从消息总线消费入站消息
     *
     * 对应 NanoBot 的 AgentLoop.run()
     */
    private suspend fun run() {
        Log.i(TAG, "AgentLoop main loop started")
        MessageBus.updateStatus(AgentStatus(AgentState.IDLE))

        for (inbound in MessageBus.inboundChannel) {
            try {
                processMessage(inbound)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error processing message: ${e.message}", e)
                sendError(inbound.chatId, "处理消息时发生错误: ${e.message}")
                MessageBus.updateStatus(AgentStatus(AgentState.IDLE))
                isProcessing = false
            }
        }
    }

    /**
     * 处理单条消息
     *
     * 对应 NanoBot 的 AgentLoop._process_message()
     */
    private suspend fun processMessage(inbound: InboundMessage) {
        if (isProcessing) {
            Log.w(TAG, "Already processing a message, queuing: ${inbound.text.take(50)}")
        }
        isProcessing = true

        try {
            // 处理系统命令
            if (inbound.text.startsWith("/")) {
                val handled = handleSystemCommand(inbound)
                if (handled) {
                    isProcessing = false
                    return
                }
            }

            // 确定会话键
            val sessionKey = inbound.sessionKeyOverride ?: currentSessionKey

            // 加载或创建会话
            val session = sessionManager.getOrCreate(sessionKey)

            // 将用户消息加入会话
            val userMessage = buildUserMessage(inbound)
            sessionManager.addMessage(sessionKey, userMessage)

            // 触发 Agent Loop
            runAgentLoop(inbound.chatId, sessionKey, session)

        } finally {
            isProcessing = false
        }
    }

    /**
     * 系统命令处理
     *
     * 对应 NanoBot 的命令处理逻辑
     */
    private suspend fun handleSystemCommand(inbound: InboundMessage): Boolean {
        val parts = inbound.text.trim().split(" ", limit = 2)
        val command = parts[0].lowercase()

        return when (command) {
            "/new", "/reset" -> {
                currentSessionKey = UUID.randomUUID().toString().take(8)
                sessionManager.getOrCreate(currentSessionKey)
                sendSystemMessage(inbound.chatId, "已开始新对话 (会话: $currentSessionKey)")
                true
            }
            "/help" -> {
                sendSystemMessage(inbound.chatId, buildHelpText())
                true
            }
            "/session" -> {
                sendSystemMessage(inbound.chatId, "当前会话: $currentSessionKey")
                true
            }
            "/memory" -> {
                val memory = memoryStore.readMemory()
                sendSystemMessage(inbound.chatId, "当前记忆:\n\n${memory.ifEmpty { "（暂无记忆）" }}")
                true
            }
            "/clear" -> {
                memoryStore.clearMemory()
                sendSystemMessage(inbound.chatId, "记忆已清空")
                true
            }
            else -> false
        }
    }

    /**
     * Agent LLM 循环（核心）
     *
     * 对应 NanoBot 的 AgentLoop._run_agent_loop()
     * 最多迭代 MAX_ITERATIONS 次，处理工具调用链
     */
    private suspend fun runAgentLoop(
        chatId: String,
        sessionKey: String,
        session: Session
    ) {
        val (provider, model) = resolveProviderAndModel()
        val tools = toolRegistry.getAll()

        var iterationCount = 0
        var done = false

        while (!done && iterationCount < MAX_ITERATIONS) {
            iterationCount++
            Log.d(TAG, "Agent loop iteration $iterationCount/$MAX_ITERATIONS")

            // 构建上下文
            MessageBus.updateStatus(AgentStatus(
                state = AgentState.THINKING,
                sessionKey = sessionKey,
                iterationCount = iterationCount,
                currentAction = "构建上下文..."
            ))

            val (systemPrompt, messages) = contextBuilder.build(sessionKey)

            // 调用 LLM
            MessageBus.updateStatus(AgentStatus(
                state = AgentState.THINKING,
                sessionKey = sessionKey,
                iterationCount = iterationCount,
                currentAction = "调用 LLM..."
            ))

            val llmResponse = try {
                provider.chatWithRetry(
                    messages = messages,
                    tools = tools,
                    model = model,
                    maxTokens = 8192,
                    systemPrompt = systemPrompt
                )
            } catch (e: Exception) {
                Log.e(TAG, "LLM call failed: ${e.message}", e)
                sendError(chatId, "LLM 调用失败: ${e.message}")
                return
            }

            Log.d(TAG, "LLM response: finish_reason=${llmResponse.finishReason}, " +
                    "has_tools=${llmResponse.hasToolCalls}, " +
                    "content_len=${llmResponse.content?.length ?: 0}, " +
                    "usage=${llmResponse.usage}")

            // 将助手响应加入会话
            val assistantMessage = buildAssistantMessage(llmResponse)
            sessionManager.addMessage(sessionKey, assistantMessage)

            // 发送思维链内容（如果有）
            llmResponse.reasoningContent?.let { thinking ->
                if (thinking.isNotBlank()) {
                    MessageBus.publishOutbound(OutboundMessage(
                        chatId = chatId,
                        text = thinking,
                        isStreaming = false,
                        isComplete = false,
                        metadata = mapOf("type" to "thinking")
                    ))
                }
            }

            // 处理工具调用
            if (llmResponse.hasToolCalls) {
                MessageBus.updateStatus(AgentStatus(
                    state = AgentState.TOOL_CALLING,
                    sessionKey = sessionKey,
                    iterationCount = iterationCount,
                    currentAction = "执行工具: ${llmResponse.toolCalls.joinToString(", ") { it.name }}"
                ))

                // 通知 UI 正在执行工具
                val toolNotify = llmResponse.toolCalls.joinToString("\n") { call ->
                    "🔧 执行工具: **${call.name}**"
                }
                MessageBus.publishOutbound(OutboundMessage(
                    chatId = chatId,
                    text = toolNotify,
                    isStreaming = false,
                    isComplete = false,
                    metadata = mapOf("type" to "tool_notify")
                ))

                // 执行所有工具调用
                val toolResults = executeToolCalls(llmResponse.toolCalls, chatId)

                // 将工具结果加入会话
                toolResults.forEach { result ->
                    val toolResultMessage = buildToolResultMessage(result)
                    sessionManager.addMessage(sessionKey, toolResultMessage)
                }

                // 继续循环让 LLM 处理工具结果
                done = false

            } else {
                // 没有工具调用，生成最终响应
                val responseText = llmResponse.content ?: ""

                if (responseText.isNotBlank()) {
                    MessageBus.updateStatus(AgentStatus(
                        state = AgentState.RESPONDING,
                        sessionKey = sessionKey,
                        iterationCount = iterationCount,
                        currentAction = "生成响应"
                    ))

                    MessageBus.publishOutbound(OutboundMessage(
                        chatId = chatId,
                        text = responseText,
                        isComplete = true
                    ))
                }
                done = true
            }
        }

        if (iterationCount >= MAX_ITERATIONS) {
            Log.w(TAG, "Reached max iterations ($MAX_ITERATIONS), stopping loop")
            sendSystemMessage(chatId, "⚠️ 已达到最大迭代次数 ($MAX_ITERATIONS)，停止处理。")
        }

        // 检查是否需要整合记忆
        checkAndConsolidateMemory(sessionKey)

        MessageBus.updateStatus(AgentStatus(AgentState.IDLE, sessionKey = sessionKey))
    }

    /**
     * 执行工具调用列表
     */
    private suspend fun executeToolCalls(
        toolCalls: List<ToolCallRequest>,
        chatId: String
    ): List<ToolResult> {
        return toolCalls.map { call ->
            Log.d(TAG, "Executing tool: ${call.name} with args: ${call.arguments.take(200)}")
            try {
                val argsMap = parseArguments(call.arguments)
                val resultText = toolExecutor.execute(call.name, argsMap)
                Log.d(TAG, "Tool ${call.name} completed, result length: ${resultText.length}")
                ToolResult(
                    toolCallId = call.id,
                    toolName = call.name,
                    success = true,
                    output = resultText
                )
            } catch (e: Exception) {
                Log.e(TAG, "Tool ${call.name} failed: ${e.message}", e)
                ToolResult(
                    toolCallId = call.id,
                    toolName = call.name,
                    success = false,
                    output = "工具执行失败: ${e.message}",
                    error = e.message
                )
            }
        }
    }

    /**
     * 检查是否需要整合记忆（对应 NanoBot 的 consolidation 逻辑）
     */
    private suspend fun checkAndConsolidateMemory(sessionKey: String) {
        val session = sessionManager.get(sessionKey) ?: return
        val unprocessedCount = session.messages.size - session.lastConsolidatedIndex

        if (unprocessedCount >= config.consolidationThreshold) {
            Log.i(TAG, "Triggering memory consolidation: $unprocessedCount unprocessed messages")
            // 在后台执行整合，不阻塞当前对话
            scope.launch {
                try {
                    consolidateMemory(sessionKey)
                } catch (e: Exception) {
                    Log.e(TAG, "Memory consolidation failed: ${e.message}", e)
                }
            }
        }
    }

    /**
     * 整合记忆（对应 NanoBot 的 MemoryConsolidator）
     */
    private suspend fun consolidateMemory(sessionKey: String) {
        val session = sessionManager.get(sessionKey) ?: return
        val messagesToProcess = session.messages.drop(session.lastConsolidatedIndex).take(50)
        if (messagesToProcess.isEmpty()) return

        Log.i(TAG, "Consolidating ${messagesToProcess.size} messages...")

        val (provider, model) = resolveProviderAndModel()

        val consolidationPrompt = """
请对以下对话历史进行简洁总结，提取重要的用户偏好、事实信息和关键决策，格式为 Markdown：

${messagesToProcess.joinToString("\n") { "[${it.role.uppercase()}] ${it.content?.take(500) ?: ""}" }}

请用以下格式输出：
## 关键信息
- （重要事实和信息）

## 用户偏好  
- （用户的偏好和习惯）

## 重要决策
- （做出的重要决策）
        """.trimIndent()

        val response = provider.chat(
            messages = listOf(ChatMessage(role = "user", content = consolidationPrompt)),
            model = model,
            maxTokens = 2048
        )

        response.content?.let { summary ->
            memoryStore.appendToHistory(summary)
            sessionManager.updateConsolidatedIndex(sessionKey, session.messages.size)
            Log.i(TAG, "Memory consolidation completed")
        }
    }

    // ==================== 工具方法 ====================

    private fun resolveProviderAndModel(): Pair<LLMProvider, String> {
        return providerRegistry.resolveProvider(config.defaultModel)
    }

    private fun buildUserMessage(inbound: InboundMessage): ChatMessage {
        return if (inbound.attachments.isEmpty()) {
            ChatMessage(role = "user", content = inbound.text)
        } else {
            // 多模态消息
            val blocks = mutableListOf<ContentBlock>()
            blocks.add(ContentBlock.Text(inbound.text))
            inbound.attachments.filter { it.type == AttachmentType.IMAGE }.forEach { att ->
                att.base64Data?.let { data ->
                    blocks.add(ContentBlock.Image(mimeType = att.mimeType, data = data))
                }
            }
            ChatMessage(role = "user", contentBlocks = blocks)
        }
    }

    private fun buildAssistantMessage(response: LLMResponse): ChatMessage {
        return if (response.hasToolCalls) {
            ChatMessage(
                role = "assistant",
                content = response.content,
                toolCalls = response.toolCalls,
                reasoningContent = response.reasoningContent
            )
        } else {
            ChatMessage(
                role = "assistant",
                content = response.content ?: "",
                reasoningContent = response.reasoningContent
            )
        }
    }

    private fun buildToolResultMessage(result: ToolResult): ChatMessage {
        return ChatMessage(
            role = "tool",
            content = result.output,
            toolCallId = result.toolCallId
        )
    }

    private fun parseArguments(argumentsJson: String): Map<String, Any> {
        return try {
            val jsonObj = Json.parseToJsonElement(argumentsJson).jsonObject
            jsonObj.entries.associate { (k, v) ->
                k to (v.toString().trim('"'))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse tool arguments: $argumentsJson", e)
            emptyMap()
        }
    }

    private suspend fun sendError(chatId: String, message: String) {
        MessageBus.publishOutbound(OutboundMessage(
            chatId = chatId,
            text = message,
            isError = true,
            isComplete = true
        ))
        MessageBus.updateStatus(AgentStatus(AgentState.ERROR, message = message))
    }

    private suspend fun sendSystemMessage(chatId: String, message: String) {
        MessageBus.publishOutbound(OutboundMessage(
            chatId = chatId,
            text = message,
            isComplete = true,
            metadata = mapOf("type" to "system")
        ))
    }

    private fun buildHelpText(): String = """
**NanoBot 命令帮助**

`/new` 或 `/reset` - 开始新对话
`/session` - 查看当前会话 ID
`/memory` - 查看当前记忆内容
`/clear` - 清空记忆
`/help` - 显示此帮助

**提示**：
- 直接输入问题开始对话
- 发送图片可进行多模态对话（需模型支持）
    """.trimIndent()
}
