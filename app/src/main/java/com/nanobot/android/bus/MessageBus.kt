package com.nanobot.android.bus

import com.nanobot.android.model.InboundMessage
import com.nanobot.android.model.OutboundMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 消息总线 - 对应 NanoBot 的 bus/queue.py
 *
 * 使用 Kotlin Channel 替代 Python asyncio.Queue
 * - inboundChannel: UI -> Agent
 * - outboundFlow: Agent -> UI (使用 SharedFlow 支持多订阅者)
 * - agentLogFlow: Agent 运行日志流（UI 日志面板订阅）
 */
object MessageBus {

    // UI -> Agent 的入站消息队列（Channel，点对点）
    private val _inboundChannel = Channel<InboundMessage>(capacity = Channel.UNLIMITED)
    val inboundChannel: Channel<InboundMessage> = _inboundChannel

    // Agent -> UI 的出站消息流（SharedFlow，支持 UI 订阅）
    private val _outboundFlow = MutableSharedFlow<OutboundMessage>(
        replay = 0,
        extraBufferCapacity = 256
    )
    val outboundFlow: SharedFlow<OutboundMessage> = _outboundFlow.asSharedFlow()

    // Agent 状态更新流
    private val _agentStatusFlow = MutableSharedFlow<AgentStatus>(
        replay = 1,
        extraBufferCapacity = 16
    )
    val agentStatusFlow: SharedFlow<AgentStatus> = _agentStatusFlow.asSharedFlow()

    // Agent 运行日志流（replay=200，UI 打开时可回放近 200 条日志）
    private val _agentLogFlow = MutableSharedFlow<AgentLogEntry>(
        replay = 200,
        extraBufferCapacity = 256
    )
    val agentLogFlow: SharedFlow<AgentLogEntry> = _agentLogFlow.asSharedFlow()

    /**
     * 发布入站消息（从 UI 发给 Agent）
     */
    fun publishInbound(message: InboundMessage) {
        _inboundChannel.trySend(message)
    }

    /**
     * 发布出站消息（从 Agent 发给 UI）
     */
    suspend fun publishOutbound(message: OutboundMessage) {
        _outboundFlow.emit(message)
    }

    /**
     * 更新 Agent 状态
     */
    suspend fun updateStatus(status: AgentStatus) {
        _agentStatusFlow.emit(status)
    }

    /**
     * 写入运行日志（AgentLoop 调用，UI 日志面板订阅）
     */
    fun emitLog(level: AgentLogLevel, tag: String, message: String) {
        _agentLogFlow.tryEmit(
            AgentLogEntry(
                level = level,
                tag = tag,
                message = message,
                timestamp = System.currentTimeMillis()
            )
        )
    }
}

/**
 * Agent 运行日志条目
 */
data class AgentLogEntry(
    val level: AgentLogLevel,
    val tag: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

enum class AgentLogLevel { VERBOSE, DEBUG, INFO, WARN, ERROR }

/**
 * Agent 运行状态
 */
data class AgentStatus(
    val state: AgentState,
    val sessionKey: String = "",
    val currentAction: String = "",
    val iterationCount: Int = 0,
    val message: String = ""
)

enum class AgentState {
    IDLE,           // 空闲
    THINKING,       // LLM 思考中
    TOOL_CALLING,   // 执行工具
    RESPONDING,     // 生成响应
    ERROR           // 错误
}
