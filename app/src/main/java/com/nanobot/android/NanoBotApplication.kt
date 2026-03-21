package com.nanobot.android

import android.app.Application
import android.util.Log
import com.nanobot.android.agent.AgentLoop
import com.nanobot.android.agent.ContextBuilder
import com.nanobot.android.agent.MemoryStore
import com.nanobot.android.model.AgentsConfig
import com.nanobot.android.model.ProvidersConfig
import com.nanobot.android.model.ProviderConfig
import com.nanobot.android.model.ToolsConfig
import com.nanobot.android.provider.AnthropicProvider
import com.nanobot.android.provider.OpenAICompatProvider
import com.nanobot.android.provider.OpenRouterProvider
import com.nanobot.android.provider.ProviderRegistry
import com.nanobot.android.session.SessionManager
import com.nanobot.android.tools.ToolExecutor
import com.nanobot.android.tools.ToolRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TAG = "NanoBotApplication"

/**
 * Application 类 - 初始化所有核心组件
 *
 * 生命周期：Application.onCreate -> AgentLoop.start
 */
class NanoBotApplication : Application() {

    private val appScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    lateinit var agentLoop: AgentLoop
        private set

    lateinit var memoryStore: MemoryStore
        private set

    lateinit var sessionManager: SessionManager
        private set

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "NanoBot Application starting...")

        initializeComponents()
        startAgentLoop()

        Log.i(TAG, "NanoBot Application started successfully")
    }

    private fun initializeComponents() {
        // 1. 存储层
        memoryStore = MemoryStore(this)
        sessionManager = SessionManager(this)

        // 2. Provider 注册表
        val providerRegistry = ProviderRegistry()
        setupProviders(providerRegistry)

        // 3. 工具系统
        val toolsConfig = ToolsConfig(
            fileReadEnabled = true,
            fileWriteEnabled = true,
            webSearchEnabled = true,
            webFetchEnabled = true,
            shellEnabled = false  // Android 不支持 shell
        )
        val toolRegistry = ToolRegistry()
        toolRegistry.registerDefaultTools(toolsConfig)
        val toolExecutor = ToolExecutor(memoryStore, toolsConfig)

        // 4. Agent 核心组件
        val agentsConfig = AgentsConfig(
            defaultModel = loadDefaultModel(),
            maxIterations = 40,
            memoryEnabled = true,
            consolidationThreshold = 50
        )

        val contextBuilder = ContextBuilder(sessionManager, memoryStore, agentsConfig)

        // 5. AgentLoop
        agentLoop = AgentLoop(
            providerRegistry = providerRegistry,
            sessionManager = sessionManager,
            toolRegistry = toolRegistry,
            toolExecutor = toolExecutor,
            contextBuilder = contextBuilder,
            memoryStore = memoryStore,
            config = agentsConfig
        )

        Log.i(TAG, "All components initialized")
    }

    private fun setupProviders(registry: ProviderRegistry) {
        // 从 SharedPreferences 加载 API Keys
        val prefs = getSharedPreferences("nanobot_config", MODE_PRIVATE)

        val openrouterKey = prefs.getString("openrouter_api_key", "") ?: ""
        val anthropicKey = prefs.getString("anthropic_api_key", "") ?: ""
        val deepseekKey = prefs.getString("deepseek_api_key", "") ?: ""
        val openaiKey = prefs.getString("openai_api_key", "") ?: ""
        val ollamaUrl = prefs.getString("ollama_base_url", "http://localhost:11434") ?: "http://localhost:11434"

        if (openrouterKey.isNotEmpty()) {
            registry.register(OpenRouterProvider(openrouterKey))
            Log.i(TAG, "OpenRouter provider registered")
        }

        if (anthropicKey.isNotEmpty()) {
            registry.register(AnthropicProvider(anthropicKey))
            Log.i(TAG, "Anthropic provider registered")
        }

        // DeepSeek
        if (deepseekKey.isNotEmpty()) {
            registry.register(OpenAICompatProvider(
                apiKey = deepseekKey,
                baseUrl = "https://api.deepseek.com/v1",
                providerName = "deepseek",
                defaultModelName = "deepseek-chat",
                modelList = listOf("deepseek-chat", "deepseek-reasoner")
            ))
            Log.i(TAG, "DeepSeek provider registered")
        }

        // OpenAI
        if (openaiKey.isNotEmpty()) {
            registry.register(OpenAICompatProvider(
                apiKey = openaiKey,
                baseUrl = "https://api.openai.com/v1",
                providerName = "openai",
                defaultModelName = "gpt-4o",
                modelList = listOf("gpt-4o", "gpt-4o-mini", "o1", "o1-mini", "o3-mini")
            ))
            Log.i(TAG, "OpenAI provider registered")
        }

        // Ollama（本地模型）
        registry.register(OpenAICompatProvider(
            apiKey = "ollama",  // Ollama 不需要真实 key
            baseUrl = "$ollamaUrl/v1",
            providerName = "ollama",
            defaultModelName = "llama3.2",
            modelList = listOf("llama3.2", "qwen2.5", "deepseek-r1", "gemma3")
        ))
        Log.i(TAG, "Ollama provider registered at $ollamaUrl")
    }

    private fun loadDefaultModel(): String {
        val prefs = getSharedPreferences("nanobot_config", MODE_PRIVATE)
        return prefs.getString("default_model", "openrouter/anthropic/claude-3.5-sonnet")
            ?: "openrouter/anthropic/claude-3.5-sonnet"
    }

    private fun startAgentLoop() {
        appScope.launch {
            agentLoop.start()
        }
        Log.i(TAG, "AgentLoop started")
    }

    override fun onTerminate() {
        super.onTerminate()
        agentLoop.stop()
        Log.i(TAG, "NanoBot Application terminated")
    }
}
