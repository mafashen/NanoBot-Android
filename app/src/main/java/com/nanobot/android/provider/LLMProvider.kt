package com.nanobot.android.provider

import com.nanobot.android.model.*

/**
 * LLM Provider 抽象基类 - 对应 NanoBot 的 providers/base.py
 */
abstract class LLMProvider {

    abstract val name: String
    abstract val defaultModel: String
    abstract val supportedModels: List<String>

    /**
     * 发送聊天请求（核心方法）
     */
    abstract suspend fun chat(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition> = emptyList(),
        model: String = defaultModel,
        maxTokens: Int = 8192,
        temperature: Float = 0.7f,
        systemPrompt: String? = null,
        reasoningEffort: String? = null  // "low", "medium", "high" for thinking models
    ): LLMResponse

    /**
     * 带重试的聊天请求（对应 NanoBot 的 chat_with_retry）
     */
    suspend fun chatWithRetry(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition> = emptyList(),
        model: String = defaultModel,
        maxTokens: Int = 8192,
        temperature: Float = 0.7f,
        systemPrompt: String? = null,
        maxRetries: Int = 3,
        reasoningEffort: String? = null
    ): LLMResponse {
        var lastException: Exception? = null
        var delayMs = 1000L

        repeat(maxRetries) { attempt ->
            try {
                return chat(messages, tools, model, maxTokens, temperature, systemPrompt, reasoningEffort)
            } catch (e: LLMRateLimitException) {
                lastException = e
                val waitMs = e.retryAfterSeconds?.let { it * 1000L } ?: (delayMs * (attempt + 1))
                kotlinx.coroutines.delay(waitMs)
            } catch (e: LLMServerException) {
                lastException = e
                kotlinx.coroutines.delay(delayMs * (attempt + 1))
                delayMs *= 2
            } catch (e: Exception) {
                throw e  // 不可重试的错误直接抛出
            }
        }
        throw lastException ?: LLMException("Unknown error after $maxRetries retries")
    }

    /**
     * 验证 API Key 是否有效
     */
    open suspend fun validateApiKey(apiKey: String): Boolean = true

    /**
     * 获取模型列表
     */
    open suspend fun listModels(): List<String> = supportedModels

    /**
     * 估算 token 数（简单实现）
     */
    open fun estimateTokens(text: String): Int = (text.length / 4).coerceAtLeast(1)
}

// ==================== 异常类 ====================

open class LLMException(message: String, cause: Throwable? = null) : Exception(message, cause)
class LLMAuthException(message: String) : LLMException(message)
class LLMRateLimitException(message: String, val retryAfterSeconds: Int? = null) : LLMException(message)
class LLMServerException(message: String, val statusCode: Int = 500) : LLMException(message)
class LLMContextLengthException(message: String) : LLMException(message)
class LLMTimeoutException(message: String) : LLMException(message)
