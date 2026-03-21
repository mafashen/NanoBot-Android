package com.nanobot.android.provider

import com.nanobot.android.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * OpenRouter Provider - 支持通过 OpenRouter 访问 100+ 模型
 *
 * OpenRouter 使用标准 OpenAI 兼容 API 格式，是最推荐的多模型入口
 * 参考 NanoBot 的 providers/registry.py 中的 openrouter 配置
 */
class OpenRouterProvider(
    private val apiKey: String,
    private val siteUrl: String = "https://nanobot.app",
    private val siteName: String = "NanoBot Android"
) : LLMProvider() {

    override val name = "openrouter"
    override val defaultModel = "anthropic/claude-3.5-sonnet"
    override val supportedModels = listOf(
        "anthropic/claude-opus-4",
        "anthropic/claude-sonnet-4",
        "anthropic/claude-3.5-sonnet",
        "anthropic/claude-3.5-haiku",
        "openai/gpt-4o",
        "openai/gpt-4o-mini",
        "openai/o1",
        "openai/o1-mini",
        "google/gemini-2.0-flash",
        "google/gemini-flash-1.5",
        "deepseek/deepseek-chat",
        "deepseek/deepseek-r1",
        "meta-llama/llama-3.3-70b-instruct",
        "qwen/qwen-2.5-72b-instruct",
        "mistralai/mistral-large"
    )

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        isLenient = true
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun chat(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>,
        model: String,
        maxTokens: Int,
        temperature: Float,
        systemPrompt: String?,
        reasoningEffort: String?
    ): LLMResponse = withContext(Dispatchers.IO) {

        val requestMessages = buildRequestMessages(messages, systemPrompt)
        val requestBody = buildRequestBody(model, requestMessages, tools, maxTokens, temperature, reasoningEffort)

        val request = Request.Builder()
            .url("https://openrouter.ai/api/v1/chat/completions")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .header("Authorization", "Bearer $apiKey")
            .header("HTTP-Referer", siteUrl)
            .header("X-Title", siteName)
            .header("Content-Type", "application/json")
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw LLMServerException("Empty response")

        if (!response.isSuccessful) {
            handleErrorResponse(response.code, responseBody)
        }

        parseResponse(responseBody)
    }

    private fun buildRequestMessages(messages: List<ChatMessage>, systemPrompt: String?): JsonArray {
        return buildJsonArray {
            // 添加系统提示词
            systemPrompt?.let {
                addJsonObject {
                    put("role", "system")
                    put("content", it)
                }
            }

            // 添加对话消息
            messages.forEach { msg ->
                addJsonObject {
                    put("role", msg.role)
                    if (msg.contentBlocks != null) {
                        // 多模态内容
                        putJsonArray("content") {
                            msg.contentBlocks.forEach { block ->
                                addJsonObject {
                                    when (block) {
                                        is ContentBlock.Text -> {
                                            put("type", "text")
                                            put("text", block.text)
                                        }
                                        is ContentBlock.Image -> {
                                            put("type", "image_url")
                                            putJsonObject("image_url") {
                                                put("url", "data:${block.mimeType};base64,${block.data}")
                                            }
                                        }
                                        is ContentBlock.ToolUse -> {
                                            // 工具调用在 tool_calls 字段，这里跳过
                                        }
                                        is ContentBlock.ToolResult -> {
                                            put("type", "tool_result")
                                            put("tool_use_id", block.toolUseId)
                                            put("content", block.content)
                                        }
                                        is ContentBlock.Thinking -> {
                                            put("type", "thinking")
                                            put("thinking", block.thinking)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // 普通文本内容
                        put("content", msg.content ?: "")
                    }

                    // 工具调用
                    msg.toolCalls?.let { calls ->
                        if (calls.isNotEmpty()) {
                            putJsonArray("tool_calls") {
                                calls.forEach { call ->
                                    addJsonObject {
                                        put("id", call.id)
                                        put("type", "function")
                                        putJsonObject("function") {
                                            put("name", call.name)
                                            put("arguments", call.arguments)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 工具结果消息
                    msg.toolCallId?.let { put("tool_call_id", it) }
                }
            }
        }
    }

    private fun buildRequestBody(
        model: String,
        messages: JsonArray,
        tools: List<ToolDefinition>,
        maxTokens: Int,
        temperature: Float,
        reasoningEffort: String?
    ): JsonObject {
        return buildJsonObject {
            put("model", model)
            put("messages", messages)
            put("max_tokens", maxTokens)
            put("temperature", temperature)

            // 工具定义
            if (tools.isNotEmpty()) {
                putJsonArray("tools") {
                    tools.forEach { tool ->
                        addJsonObject {
                            put("type", "function")
                            putJsonObject("function") {
                                put("name", tool.name)
                                put("description", tool.description)
                                putJsonObject("parameters") {
                                    put("type", "object")
                                    putJsonObject("properties") {
                                        tool.parameters.properties.forEach { (propName, prop) ->
                                            putJsonObject(propName) {
                                                put("type", prop.type)
                                                if (prop.description.isNotEmpty()) put("description", prop.description)
                                                prop.enum?.let { enumValues ->
                                                    putJsonArray("enum") { enumValues.forEach { add(it) } }
                                                }
                                            }
                                        }
                                    }
                                    if (tool.parameters.required.isNotEmpty()) {
                                        putJsonArray("required") {
                                            tool.parameters.required.forEach { add(it) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                put("tool_choice", "auto")
            }

            // reasoning_effort（用于支持思维链的模型）
            reasoningEffort?.let {
                putJsonObject("reasoning") {
                    put("effort", it)
                }
            }
        }
    }

    private fun parseResponse(responseBody: String): LLMResponse {
        val jsonObj = Json.parseToJsonElement(responseBody).jsonObject
        val choice = jsonObj["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: throw LLMException("No choices in response")

        val message = choice["message"]?.jsonObject
            ?: throw LLMException("No message in choice")

        val finishReason = choice["finish_reason"]?.jsonPrimitive?.content ?: "stop"
        val content = message["content"]?.jsonPrimitive?.contentOrNull

        // 解析工具调用
        val toolCalls = message["tool_calls"]?.jsonArray?.map { callJson ->
            val callObj = callJson.jsonObject
            val func = callObj["function"]?.jsonObject
            ToolCallRequest(
                id = callObj["id"]?.jsonPrimitive?.content ?: "",
                name = func?.get("name")?.jsonPrimitive?.content ?: "",
                arguments = func?.get("arguments")?.jsonPrimitive?.content ?: "{}"
            )
        } ?: emptyList()

        // 解析 token 使用
        val usage = jsonObj["usage"]?.jsonObject?.let { u ->
            UsageInfo(
                promptTokens = u["prompt_tokens"]?.jsonPrimitive?.int ?: 0,
                completionTokens = u["completion_tokens"]?.jsonPrimitive?.int ?: 0,
                totalTokens = u["total_tokens"]?.jsonPrimitive?.int ?: 0
            )
        }

        // 解析 reasoning content（思维链）
        val reasoningContent = message["reasoning"]?.jsonPrimitive?.contentOrNull

        return LLMResponse(
            content = content,
            toolCalls = toolCalls,
            finishReason = finishReason,
            usage = usage,
            reasoningContent = reasoningContent
        )
    }

    private fun handleErrorResponse(statusCode: Int, body: String): Nothing {
        when (statusCode) {
            401, 403 -> throw LLMAuthException("API Key 无效或无权限: $body")
            429 -> {
                // 尝试解析 retry-after
                val retryAfter = Regex("\"retry_after\":(\\d+)").find(body)?.groupValues?.get(1)?.toIntOrNull()
                throw LLMRateLimitException("请求频率超限", retryAfter)
            }
            400 -> {
                if (body.contains("context_length_exceeded") || body.contains("maximum context length")) {
                    throw LLMContextLengthException("上下文长度超出限制: $body")
                }
                throw LLMException("请求参数错误 (400): $body")
            }
            in 500..599 -> throw LLMServerException("服务器错误 ($statusCode): $body", statusCode)
            else -> throw LLMException("未知错误 ($statusCode): $body")
        }
    }
}

/**
 * Anthropic Provider - 直接调用 Claude API
 *
 * 参考 NanoBot 的 providers/anthropic_direct.py（如有）和 registry.py
 */
class AnthropicProvider(
    private val apiKey: String
) : LLMProvider() {

    override val name = "anthropic"
    override val defaultModel = "claude-3-5-sonnet-20241022"
    override val supportedModels = listOf(
        "claude-opus-4-5",
        "claude-sonnet-4-5",
        "claude-3-5-sonnet-20241022",
        "claude-3-5-haiku-20241022",
        "claude-3-opus-20240229",
        "claude-3-haiku-20240307"
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun chat(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>,
        model: String,
        maxTokens: Int,
        temperature: Float,
        systemPrompt: String?,
        reasoningEffort: String?
    ): LLMResponse = withContext(Dispatchers.IO) {

        val requestBody = buildRequestBody(messages, tools, model, maxTokens, temperature, systemPrompt, reasoningEffort)

        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("anthropic-beta", "prompt-caching-2024-07-31")
            .header("Content-Type", "application/json")
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw LLMServerException("Empty response")

        if (!response.isSuccessful) {
            handleErrorResponse(response.code, responseBody)
        }

        parseAnthropicResponse(responseBody)
    }

    private fun buildRequestBody(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>,
        model: String,
        maxTokens: Int,
        temperature: Float,
        systemPrompt: String?,
        reasoningEffort: String?
    ): JsonObject {
        return buildJsonObject {
            put("model", model)
            put("max_tokens", maxTokens)
            if (reasoningEffort == null) {
                put("temperature", temperature)
            }

            // 系统提示词（Anthropic 格式支持 cache_control）
            systemPrompt?.let { sys ->
                putJsonArray("system") {
                    addJsonObject {
                        put("type", "text")
                        put("text", sys)
                        // 对长系统提示词开启缓存
                        if (sys.length > 1000) {
                            putJsonObject("cache_control") {
                                put("type", "ephemeral")
                            }
                        }
                    }
                }
            }

            // 消息
            putJsonArray("messages") {
                messages.forEach { msg ->
                    addJsonObject {
                        put("role", msg.role)
                        if (msg.contentBlocks != null) {
                            putJsonArray("content") {
                                msg.contentBlocks.forEach { block ->
                                    addJsonObject {
                                        when (block) {
                                            is ContentBlock.Text -> {
                                                put("type", "text")
                                                put("text", block.text)
                                            }
                                            is ContentBlock.Image -> {
                                                put("type", "image")
                                                putJsonObject("source") {
                                                    put("type", "base64")
                                                    put("media_type", block.mimeType)
                                                    put("data", block.data)
                                                }
                                            }
                                            is ContentBlock.ToolUse -> {
                                                put("type", "tool_use")
                                                put("id", block.id)
                                                put("name", block.name)
                                                put("input", Json.parseToJsonElement(block.input))
                                            }
                                            is ContentBlock.ToolResult -> {
                                                put("type", "tool_result")
                                                put("tool_use_id", block.toolUseId)
                                                put("content", block.content)
                                                if (block.isError) put("is_error", true)
                                            }
                                            is ContentBlock.Thinking -> {
                                                put("type", "thinking")
                                                put("thinking", block.thinking)
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            put("content", msg.content ?: "")
                        }
                    }
                }
            }

            // 工具定义
            if (tools.isNotEmpty()) {
                putJsonArray("tools") {
                    tools.forEach { tool ->
                        addJsonObject {
                            put("name", tool.name)
                            put("description", tool.description)
                            putJsonObject("input_schema") {
                                put("type", "object")
                                putJsonObject("properties") {
                                    tool.parameters.properties.forEach { (propName, prop) ->
                                        putJsonObject(propName) {
                                            put("type", prop.type)
                                            if (prop.description.isNotEmpty()) put("description", prop.description)
                                            prop.enum?.let { enumValues ->
                                                putJsonArray("enum") { enumValues.forEach { add(it) } }
                                            }
                                        }
                                    }
                                }
                                if (tool.parameters.required.isNotEmpty()) {
                                    putJsonArray("required") {
                                        tool.parameters.required.forEach { add(it) }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 思维模式（extended thinking）
            reasoningEffort?.let {
                putJsonObject("thinking") {
                    put("type", "enabled")
                    put("budget_tokens", when (it) {
                        "low" -> 5000
                        "high" -> 30000
                        else -> 10000  // medium
                    })
                }
            }
        }
    }

    private fun parseAnthropicResponse(responseBody: String): LLMResponse {
        val jsonObj = Json.parseToJsonElement(responseBody).jsonObject
        val contentArray = jsonObj["content"]?.jsonArray ?: return LLMResponse()

        var textContent: String? = null
        val toolCalls = mutableListOf<ToolCallRequest>()
        var thinkingContent: String? = null
        val thinkingBlocks = mutableListOf<String>()

        contentArray.forEach { block ->
            val blockObj = block.jsonObject
            when (blockObj["type"]?.jsonPrimitive?.content) {
                "text" -> {
                    textContent = blockObj["text"]?.jsonPrimitive?.content
                }
                "tool_use" -> {
                    toolCalls.add(ToolCallRequest(
                        id = blockObj["id"]?.jsonPrimitive?.content ?: "",
                        name = blockObj["name"]?.jsonPrimitive?.content ?: "",
                        arguments = blockObj["input"]?.toString() ?: "{}"
                    ))
                }
                "thinking" -> {
                    val thinking = blockObj["thinking"]?.jsonPrimitive?.content ?: ""
                    thinkingBlocks.add(thinking)
                    if (thinkingContent == null) thinkingContent = thinking
                }
            }
        }

        val stopReason = jsonObj["stop_reason"]?.jsonPrimitive?.content ?: "end_turn"
        val usage = jsonObj["usage"]?.jsonObject?.let { u ->
            UsageInfo(
                promptTokens = u["input_tokens"]?.jsonPrimitive?.int ?: 0,
                completionTokens = u["output_tokens"]?.jsonPrimitive?.int ?: 0,
                totalTokens = (u["input_tokens"]?.jsonPrimitive?.int ?: 0) + (u["output_tokens"]?.jsonPrimitive?.int ?: 0),
                cacheCreationTokens = u["cache_creation_input_tokens"]?.jsonPrimitive?.int ?: 0,
                cacheReadTokens = u["cache_read_input_tokens"]?.jsonPrimitive?.int ?: 0
            )
        }

        return LLMResponse(
            content = textContent,
            toolCalls = toolCalls,
            finishReason = stopReason,
            usage = usage,
            reasoningContent = thinkingContent,
            thinkingBlocks = thinkingBlocks
        )
    }

    private fun handleErrorResponse(statusCode: Int, body: String): Nothing {
        when (statusCode) {
            401 -> throw LLMAuthException("Anthropic API Key 无效: $body")
            429 -> throw LLMRateLimitException("请求频率超限")
            400 -> {
                if (body.contains("context_window")) throw LLMContextLengthException("上下文超出限制")
                throw LLMException("请求参数错误 (400): $body")
            }
            in 500..599 -> throw LLMServerException("Anthropic 服务器错误 ($statusCode): $body", statusCode)
            else -> throw LLMException("未知错误 ($statusCode): $body")
        }
    }
}

/**
 * DeepSeek Provider - 直接调用 DeepSeek API
 *
 * 支持 deepseek-chat 和 deepseek-reasoner（R1 思维链模型）
 */
class DeepSeekProvider(
    private val apiKey: String
) : LLMProvider() {

    override val name = "deepseek"
    override val defaultModel = "deepseek-chat"
    override val supportedModels = listOf(
        "deepseek-chat",        // DeepSeek V3
        "deepseek-reasoner"     // DeepSeek R1
    )

    private val openAICompatProvider = OpenAICompatProvider(
        apiKey = apiKey,
        baseUrl = "https://api.deepseek.com/v1",
        providerName = "deepseek",
        defaultModelName = "deepseek-chat",
        modelList = supportedModels
    )

    override suspend fun chat(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>,
        model: String,
        maxTokens: Int,
        temperature: Float,
        systemPrompt: String?,
        reasoningEffort: String?
    ): LLMResponse = openAICompatProvider.chat(messages, tools, model, maxTokens, temperature, systemPrompt, reasoningEffort)
}

/**
 * OpenAI 兼容通用 Provider - 可用于所有 OpenAI 格式 API
 *
 * 支持 Ollama、vLLM、本地 LLM 等
 */
class OpenAICompatProvider(
    private val apiKey: String,
    private val baseUrl: String,
    providerName: String,
    defaultModelName: String,
    modelList: List<String> = emptyList()
) : LLMProvider() {

    override val name = providerName
    override val defaultModel = defaultModelName
    override val supportedModels = modelList

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun chat(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>,
        model: String,
        maxTokens: Int,
        temperature: Float,
        systemPrompt: String?,
        reasoningEffort: String?
    ): LLMResponse = withContext(Dispatchers.IO) {

        val requestBody = buildOpenAIRequest(messages, tools, model, maxTokens, temperature, systemPrompt)

        val request = Request.Builder()
            .url("$baseUrl/chat/completions")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw LLMServerException("Empty response")

        if (!response.isSuccessful) {
            when (response.code) {
                401, 403 -> throw LLMAuthException("API Key 无效")
                429 -> throw LLMRateLimitException("请求频率超限")
                in 500..599 -> throw LLMServerException("服务器错误 (${response.code}): $responseBody", response.code)
                else -> throw LLMException("API 错误 (${response.code}): $responseBody")
            }
        }

        parseOpenAIResponse(responseBody)
    }

    private fun buildOpenAIRequest(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>,
        model: String,
        maxTokens: Int,
        temperature: Float,
        systemPrompt: String?
    ): JsonObject = buildJsonObject {
        put("model", model)
        put("max_tokens", maxTokens)
        put("temperature", temperature)

        putJsonArray("messages") {
            systemPrompt?.let {
                addJsonObject { put("role", "system"); put("content", it) }
            }
            messages.forEach { msg ->
                addJsonObject {
                    put("role", msg.role)
                    put("content", msg.content ?: "")
                    msg.toolCalls?.let { calls ->
                        if (calls.isNotEmpty()) {
                            putJsonArray("tool_calls") {
                                calls.forEach { call ->
                                    addJsonObject {
                                        put("id", call.id)
                                        put("type", "function")
                                        putJsonObject("function") {
                                            put("name", call.name)
                                            put("arguments", call.arguments)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    msg.toolCallId?.let { put("tool_call_id", it) }
                }
            }
        }

        if (tools.isNotEmpty()) {
            putJsonArray("tools") {
                tools.forEach { tool ->
                    addJsonObject {
                        put("type", "function")
                        putJsonObject("function") {
                            put("name", tool.name)
                            put("description", tool.description)
                            putJsonObject("parameters") {
                                put("type", "object")
                                putJsonObject("properties") {
                                    tool.parameters.properties.forEach { (k, v) ->
                                        putJsonObject(k) {
                                            put("type", v.type)
                                            if (v.description.isNotEmpty()) put("description", v.description)
                                        }
                                    }
                                }
                                if (tool.parameters.required.isNotEmpty()) {
                                    putJsonArray("required") { tool.parameters.required.forEach { add(it) } }
                                }
                            }
                        }
                    }
                }
            }
            put("tool_choice", "auto")
        }
    }

    private fun parseOpenAIResponse(responseBody: String): LLMResponse {
        val jsonObj = Json.parseToJsonElement(responseBody).jsonObject
        val choice = jsonObj["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: throw LLMException("No choices in response")
        val message = choice["message"]?.jsonObject ?: throw LLMException("No message")
        val finishReason = choice["finish_reason"]?.jsonPrimitive?.content ?: "stop"
        val content = message["content"]?.jsonPrimitive?.contentOrNull
        val toolCalls = message["tool_calls"]?.jsonArray?.map { c ->
            val cObj = c.jsonObject
            val func = cObj["function"]?.jsonObject
            ToolCallRequest(
                id = cObj["id"]?.jsonPrimitive?.content ?: "",
                name = func?.get("name")?.jsonPrimitive?.content ?: "",
                arguments = func?.get("arguments")?.jsonPrimitive?.content ?: "{}"
            )
        } ?: emptyList()

        val usage = jsonObj["usage"]?.jsonObject?.let { u ->
            UsageInfo(
                promptTokens = u["prompt_tokens"]?.jsonPrimitive?.int ?: 0,
                completionTokens = u["completion_tokens"]?.jsonPrimitive?.int ?: 0,
                totalTokens = u["total_tokens"]?.jsonPrimitive?.int ?: 0
            )
        }

        // DeepSeek reasoning_content
        val reasoningContent = message["reasoning_content"]?.jsonPrimitive?.contentOrNull

        return LLMResponse(
            content = content,
            toolCalls = toolCalls,
            finishReason = finishReason,
            usage = usage,
            reasoningContent = reasoningContent
        )
    }
}

/**
 * Provider 注册表 - 管理所有 Provider 实例
 *
 * 对应 NanoBot 的 providers/registry.py
 */
class ProviderRegistry {

    private val providers = mutableMapOf<String, LLMProvider>()

    fun register(provider: LLMProvider) {
        providers[provider.name] = provider
    }

    fun get(name: String): LLMProvider? = providers[name]

    fun getAll(): Map<String, LLMProvider> = providers.toMap()

    fun getOrDefault(name: String, default: String = "openrouter"): LLMProvider {
        return providers[name] ?: providers[default]
            ?: throw IllegalStateException("No provider available: $name or $default")
    }

    /**
     * 根据模型名称自动选择 Provider
     *
     * 模型名称格式: "provider/model" 或 "model"
     * 例: "openrouter/anthropic/claude-3.5-sonnet" -> openrouter provider
     *     "anthropic/claude-3.5-sonnet" -> anthropic provider
     *     "deepseek-chat" -> deepseek provider
     */
    fun resolveProvider(modelName: String): Pair<LLMProvider, String> {
        // 处理 "openrouter/xxx" 前缀
        if (modelName.startsWith("openrouter/")) {
            val actualModel = modelName.removePrefix("openrouter/")
            val provider = providers["openrouter"] ?: throw IllegalStateException("OpenRouter provider not configured")
            return Pair(provider, actualModel)
        }

        // 按前缀匹配 provider
        providers.keys.forEach { providerName ->
            if (modelName.startsWith("$providerName/")) {
                val actualModel = modelName.removePrefix("$providerName/")
                val provider = providers[providerName]!!
                return Pair(provider, actualModel)
            }
        }

        // 尝试按模型名匹配
        val deepseekModels = listOf("deepseek-chat", "deepseek-reasoner", "deepseek-v")
        if (deepseekModels.any { modelName.startsWith(it) }) {
            val provider = providers["deepseek"] ?: providers["openrouter"]
                ?: throw IllegalStateException("No suitable provider for $modelName")
            return Pair(provider, modelName)
        }

        // 默认使用 openrouter
        val defaultProvider = providers["openrouter"] ?: providers.values.firstOrNull()
            ?: throw IllegalStateException("No providers configured")
        return Pair(defaultProvider, modelName)
    }
}
