package com.nanobot.android.agent

import com.nanobot.android.model.*
import com.nanobot.android.session.SessionManager
import com.nanobot.android.data.SettingsRepository
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
 * 4. 根据 assistantStyle 动态调整风格（lively / professional）
 */
class ContextBuilder(
    private val sessionManager: SessionManager,
    private val memoryStore: MemoryStore,
    private val config: AgentsConfig = AgentsConfig(),
    private val settingsRepository: SettingsRepository? = null
) {

    /**
     * 构建完整上下文，返回 (systemPrompt, messages)
     */
    suspend fun build(sessionKey: String): Pair<String, List<ChatMessage>> {
        val style = settingsRepository?.settings?.value?.assistantStyle ?: "professional"
        val systemPrompt = buildSystemPrompt(style)
        val messages = buildMessages(sessionKey)
        return Pair(systemPrompt, messages)
    }

    /**
     * 构建系统提示词
     *
     * 对应 NanoBot 的 context.build_system_prompt()
     */
    private suspend fun buildSystemPrompt(style: String): String {
        val parts = mutableListOf<String>()

        // 1. 身份定义（根据风格不同）
        parts.add(buildIdentitySection(style))

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

        // 6. 指导方针（根据风格不同）
        parts.add(buildGuidelinesSection(style))

        return parts.joinToString("\n\n")
    }

    private fun buildIdentitySection(style: String): String = when (style) {
        "lively" -> """
# NanoBot 🤖

嗨！我是 NanoBot，你的专属 AI 小助手，就住在你的 Android 手机里！

我的特点：
- **活力满满**：用轻松有趣的方式和你聊天，绝不死板
- **贴心懂你**：记得你的喜好和习惯，越聊越默契
- **能力强大**：搜网络、读文件、做分析，通通不在话下
- **诚实坦率**：不知道的事儿我会直说，不会瞎编
        """.trimIndent()
        else -> """
# NanoBot

你是 NanoBot，一个运行在 Android 设备上的个人 AI 助手。你直接在用户的手机上运行，与用户建立深度的个人关系。

你的核心特点：
- **个人化**：你了解用户的偏好和习惯，提供个性化的帮助
- **主动性**：在需要时主动提出建议，而不仅仅被动回答问题
- **能力广泛**：你可以使用工具完成复杂任务，包括搜索网络、读取文件等
- **诚实可靠**：你会坦诚告知自己的局限性
        """.trimIndent()
    }

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

    private fun buildGuidelinesSection(style: String): String = when (style) {
        "lively" -> """
## 行为准则

1. **轻松有趣**：用活泼的语气交流，可以适当用 emoji，让对话更有活力 ✨
2. **简洁明了**：说重点，别啰嗦，但该解释的地方要解释清楚
3. **主动出击**：看到机会就主动提建议，别等用户问了再说
4. **用工具**：需要查信息或做任务时，马上用工具，不要光靠记忆猜
5. **格式化**：代码块、列表用起来，让回复好看又好读
6. **大事要确认**：要删文件、改系统这类大操作，先问一声确认
        """.trimIndent()
        else -> """
## 行为准则

1. **简洁优先**：回复要简洁清晰，避免不必要的冗长
2. **主动使用工具**：当需要查找信息或执行任务时，主动使用可用工具
3. **格式化输出**：使用 Markdown 格式化代码块、列表等
4. **确认重要操作**：在执行可能影响文件或系统的操作前，先确认用户意图
5. **记住用户偏好**：将重要的用户偏好和信息记录到记忆中
        """.trimIndent()
    }

    /**
     * 构建消息列表
     */
    private fun buildMessages(sessionKey: String): List<ChatMessage> {
        val session = sessionManager.get(sessionKey) ?: return emptyList()
        // 返回会话历史消息（不包含系统提示词，系统提示词单独传递）
        return session.messages.filter { it.role != "system" }
    }
}
