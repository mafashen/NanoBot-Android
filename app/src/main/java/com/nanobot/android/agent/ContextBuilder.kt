package com.nanobot.android.agent

import com.nanobot.android.model.*
import com.nanobot.android.session.SessionManager
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * ContextBuilder - 构建 LLM 调用的完整上下文
 *
 * 对应 NanoBot 的 agent/context.py
 *
 * 负责：
 * 1. 构建系统提示词（身份 + 运行时环境 + 工作空间 + 记忆 + 技能）
 * 2. 组装消息列表（历史 + 最新消息）
 * 3. 支持多模态内容
 */
class ContextBuilder(
    private val sessionManager: SessionManager,
    private val memoryStore: MemoryStore,
    private val config: AgentsConfig = AgentsConfig()
) {

    /**
     * 构建完整上下文，返回 (systemPrompt, messages)
     */
    suspend fun build(sessionKey: String): Pair<String, List<ChatMessage>> {
        val systemPrompt = buildSystemPrompt()
        val messages = buildMessages(sessionKey)
        return Pair(systemPrompt, messages)
    }

    /**
     * 构建系统提示词
     *
     * 对应 NanoBot 的 context.build_system_prompt()
     */
    private suspend fun buildSystemPrompt(): String {
        val parts = mutableListOf<String>()

        // 1. 身份定义
        parts.add(buildIdentitySection())

        // 2. 运行时环境信息
        parts.add(buildRuntimeSection())

        // 3. 工作空间信息
        if (config.workspacePath.isNotEmpty()) {
            parts.add(buildWorkspaceSection())
        }

        // 4. 记忆内容
        val memory = memoryStore.readMemory()
        if (memory.isNotEmpty()) {
            parts.add("## 长期记忆\n\n$memory")
        }

        // 5. 额外的系统提示词
        if (config.systemPromptExtra.isNotEmpty()) {
            parts.add(config.systemPromptExtra)
        }

        // 6. 指导方针
        parts.add(buildGuidelinesSection())

        return parts.joinToString("\n\n")
    }

    private fun buildIdentitySection(): String = """
# NanoBot

你是 NanoBot，一个运行在 Android 设备上的个人 AI 助手。你直接在用户的手机上运行，与用户建立深度的个人关系。

你的核心特点：
- **个人化**：你了解用户的偏好和习惯，提供个性化的帮助
- **主动性**：在需要时主动提出建议，而不仅仅被动回答问题
- **能力广泛**：你可以使用工具完成复杂任务，包括搜索网络、读取文件等
- **诚实可靠**：你会坦诚告知自己的局限性
    """.trimIndent()

    private fun buildRuntimeSection(): String {
        val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        return """
## 运行时环境

- **平台**：Android (NanoBot App)
- **当前时间**：$now
- **界面**：移动端聊天界面
        """.trimIndent()
    }

    private fun buildWorkspaceSection(): String = """
## 工作空间

工作目录: `${config.workspacePath}`

你可以访问此目录中的文件，使用文件读取工具查看内容。
    """.trimIndent()

    private fun buildGuidelinesSection(): String = """
## 行为准则

1. **简洁优先**：回复要简洁清晰，避免不必要的冗长
2. **主动使用工具**：当需要查找信息或执行任务时，主动使用可用工具
3. **格式化输出**：使用 Markdown 格式化代码块、列表等
4. **确认重要操作**：在执行可能影响文件或系统的操作前，先确认用户意图
5. **记住用户偏好**：将重要的用户偏好和信息记录到记忆中
    """.trimIndent()

    /**
     * 构建消息列表
     */
    private fun buildMessages(sessionKey: String): List<ChatMessage> {
        val session = sessionManager.get(sessionKey) ?: return emptyList()
        // 返回会话历史消息（不包含系统提示词，系统提示词单独传递）
        return session.messages.filter { it.role != "system" }
    }
}
