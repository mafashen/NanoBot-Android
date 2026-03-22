package com.nanobot.android

import android.app.Application
import android.util.Log
import com.nanobot.android.agent.AgentLoop
import com.nanobot.android.agent.ContextBuilder
import com.nanobot.android.agent.MemoryStore
import com.nanobot.android.data.AppSettings
import com.nanobot.android.data.SettingsRepository
import com.nanobot.android.model.AgentsConfig
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private const val TAG = "NanoBotApplication"

/**
 * Application 类 - 初始化所有核心组件
 *
 * 支持热重载：监听 SettingsRepository.settings 的变化，
 * 当用户在设置页面保存新配置时，自动重新注册 Provider，无需重启 App。
 */
class NanoBotApplication : Application() {

    private val appScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    lateinit var agentLoop: AgentLoop
        private set

    lateinit var memoryStore: MemoryStore
        private set

    lateinit var sessionManager: SessionManager
        private set

    // Provider 注册表（热重载时整体替换）
    private lateinit var providerRegistry: ProviderRegistry

    // AgentsConfig 引用（热重载时更新 defaultModel）
    private var agentsConfig: AgentsConfig = AgentsConfig()

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "NanoBot Application starting...")

        initializeComponents()
        startAgentLoop()
        observeSettingsChanges()

        Log.i(TAG, "NanoBot Application started successfully")
    }

    private fun initializeComponents() {
        // 1. 存储层
        memoryStore = MemoryStore(this)
        sessionManager = SessionManager(this)

        // 2. Provider 注册表（从加密配置读取 Key）
        val settings = SettingsRepository.get(this).settings.value
        providerRegistry = ProviderRegistry()
        registerProviders(providerRegistry, settings)

        // 3. 工具系统
        val toolsConfig = ToolsConfig(
            fileReadEnabled = true,
            fileWriteEnabled = true,
            webSearchEnabled = true,
            webFetchEnabled = true,
            shellEnabled = false
        )
        val toolRegistry = ToolRegistry()
        toolRegistry.registerDefaultTools(toolsConfig)
        val toolExecutor = ToolExecutor(memoryStore, toolsConfig)

        // 4. Agent 核心组件
        agentsConfig = AgentsConfig(
            defaultModel = settings.defaultModel,
            maxIterations = 40,
            memoryEnabled = true,
            consolidationThreshold = 50
        )

        val contextBuilder = ContextBuilder(
            sessionManager = sessionManager,
            memoryStore = memoryStore,
            config = agentsConfig,
            settingsRepository = SettingsRepository.get(this)
        )

        // 5. AgentLoop（持有 providerRegistry 的引用，热重载时通过 updateProviderRegistry 更新）
        agentLoop = AgentLoop(
            providerRegistry = providerRegistry,
            sessionManager = sessionManager,
            toolRegistry = toolRegistry,
            toolExecutor = toolExecutor,
            contextBuilder = contextBuilder,
            memoryStore = memoryStore,
            config = agentsConfig
        )

        Log.i(TAG, "All components initialized with defaultModel=${settings.defaultModel}")
    }

    /**
     * 监听配置变更，实现热重载（无需重启 App）
     *
     * 只在以下字段变化时触发重载，避免无意义的重建：
     * - 任意 API Key
     * - Ollama URL
     * - defaultModel
     */
    private fun observeSettingsChanges() {
        appScope.launch {
            SettingsRepository.get(this@NanoBotApplication).settings
                .map { s ->
                    // 提取用于重载判断的关键字段
                    listOf(
                        s.openrouterApiKey, s.anthropicApiKey, s.deepseekApiKey,
                        s.openaiApiKey, s.ollamaBaseUrl,
                        s.customApiKey, s.customBaseUrl, s.customProviderName,
                        s.defaultModel, s.assistantStyle
                    )
                }
                .distinctUntilChanged()
                // 跳过第一次（已在 initializeComponents 初始化过了）
                .let { flow ->
                    var isFirst = true
                    flow.collect { _ ->
                        if (isFirst) {
                            isFirst = false
                            return@collect
                        }
                        val newSettings = SettingsRepository.get(this@NanoBotApplication).settings.value
                        Log.i(TAG, "Settings changed, hot-reloading providers...")
                        reloadProviders(newSettings)
                    }
                }
        }
    }

    /**
     * 热重载：重新注册所有 Provider 并更新 AgentLoop 的默认模型
     */
    private fun reloadProviders(settings: AppSettings) {
        val newRegistry = ProviderRegistry()
        registerProviders(newRegistry, settings)
        agentLoop.updateProviderRegistry(newRegistry, settings.defaultModel)
        Log.i(TAG, "Providers reloaded. defaultModel=${settings.defaultModel}")
    }

    /**
     * 向 registry 注册所有配置了 Key 的 Provider
     */
    private fun registerProviders(registry: ProviderRegistry, settings: AppSettings) {
        if (settings.openrouterApiKey.isNotEmpty()) {
            registry.register(OpenRouterProvider(settings.openrouterApiKey))
            Log.i(TAG, "OpenRouter provider registered")
        }

        if (settings.anthropicApiKey.isNotEmpty()) {
            registry.register(AnthropicProvider(settings.anthropicApiKey))
            Log.i(TAG, "Anthropic provider registered")
        }

        if (settings.deepseekApiKey.isNotEmpty()) {
            registry.register(OpenAICompatProvider(
                apiKey = settings.deepseekApiKey,
                baseUrl = "https://api.deepseek.com/v1",
                providerName = "deepseek",
                defaultModelName = "deepseek-chat",
                modelList = listOf("deepseek-chat", "deepseek-reasoner")
            ))
            Log.i(TAG, "DeepSeek provider registered")
        }

        if (settings.openaiApiKey.isNotEmpty()) {
            registry.register(OpenAICompatProvider(
                apiKey = settings.openaiApiKey,
                baseUrl = "https://api.openai.com/v1",
                providerName = "openai",
                defaultModelName = "gpt-4o",
                modelList = listOf("gpt-4o", "gpt-4o-mini", "o1", "o1-mini", "o3-mini")
            ))
            Log.i(TAG, "OpenAI provider registered")
        }

        // Ollama 始终注册（无需 Key，只要 URL 不为空）
        val ollamaUrl = settings.ollamaBaseUrl.ifEmpty { "http://localhost:11434" }
        registry.register(OpenAICompatProvider(
            apiKey = "ollama",
            baseUrl = "$ollamaUrl/v1",
            providerName = "ollama",
            defaultModelName = "llama3.2",
            modelList = listOf("llama3.2", "qwen2.5", "deepseek-r1", "gemma3")
        ))
        Log.i(TAG, "Ollama provider registered at $ollamaUrl")

        // 自定义 OpenAI 兼容 Provider
        if (settings.customApiKey.isNotEmpty() && settings.customBaseUrl.isNotEmpty()) {
            val name = settings.customProviderName.ifEmpty { "custom" }
            registry.register(OpenAICompatProvider(
                apiKey = settings.customApiKey,
                baseUrl = settings.customBaseUrl,
                providerName = name,
                defaultModelName = settings.customDefaultModel.ifEmpty { "gpt-3.5-turbo" }
            ))
            Log.i(TAG, "Custom provider '$name' registered at ${settings.customBaseUrl}")
        }
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
