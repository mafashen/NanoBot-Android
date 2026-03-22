package com.nanobot.android.data

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "SettingsRepository"

/**
 * 用户配置模型
 *
 * 包含所有 Provider 的 API Key 和默认模型配置。
 * 敏感字段（API Key）通过 EncryptedSharedPreferences 加密存储在设备 KeyStore 中，
 * 即使 root 也无法从其他 app 读取。
 */
data class AppSettings(
    // 默认使用的模型（格式: provider/model）
    val defaultModel: String = "openrouter/anthropic/claude-3.5-sonnet",

    // OpenRouter（支持 100+ 模型，推荐）
    val openrouterApiKey: String = "",

    // Anthropic 直连
    val anthropicApiKey: String = "",

    // DeepSeek
    val deepseekApiKey: String = "",

    // OpenAI
    val openaiApiKey: String = "",

    // Ollama / 自托管 OpenAI 兼容服务
    val ollamaBaseUrl: String = "http://192.168.1.x:11434",

    // 自定义 OpenAI 兼容 Endpoint（可选，留空不启用）
    val customApiKey: String = "",
    val customBaseUrl: String = "",
    val customProviderName: String = "",
    val customDefaultModel: String = "",

    // 助手风格：lively（活泼俏皮）/ professional（严谨专业）
    val assistantStyle: String = "professional"
)

/**
 * SettingsRepository - 配置管理仓库
 *
 * 安全设计：
 * 1. 所有内容均存储在 EncryptedSharedPreferences（AES256-SIV 加密 key，AES256-GCM 加密 value）
 * 2. 加密主密钥存储在 Android KeyStore，硬件隔离，不可导出
 * 3. SharedPreferences 文件对其他 app 不可读（Android 私有存储）
 * 4. 内存中的 StateFlow 不做任何日志打印（避免 logcat 泄露）
 *
 * 使用方式：
 *   SettingsRepository.get(context).settings.collect { settings -> ... }
 *   SettingsRepository.get(context).save(newSettings)
 */
class SettingsRepository private constructor(context: Context) {

    private val appContext = context.applicationContext

    // EncryptedSharedPreferences：底层用 Android KeyStore 保护的 AES-256 密钥加密
    private val encryptedPrefs by lazy {
        try {
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                appContext,
                PREFS_FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create EncryptedSharedPreferences, falling back", e)
            // 降级：极少数设备 KeyStore 损坏时的兜底（功能正常，安全性降低）
            appContext.getSharedPreferences(PREFS_FILE_NAME + "_fallback", Context.MODE_PRIVATE)
        }
    }

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    /**
     * 保存配置。会立即更新 StateFlow，通知所有订阅者（AgentLoop 等组件会自动热重载）。
     * API Key 为空时自动 trim，避免用户不小心输入空格导致认证失败。
     */
    fun save(settings: AppSettings) {
        val sanitized = settings.copy(
            defaultModel = settings.defaultModel.trim(),
            openrouterApiKey = settings.openrouterApiKey.trim(),
            anthropicApiKey = settings.anthropicApiKey.trim(),
            deepseekApiKey = settings.deepseekApiKey.trim(),
            openaiApiKey = settings.openaiApiKey.trim(),
            ollamaBaseUrl = settings.ollamaBaseUrl.trim(),
            customApiKey = settings.customApiKey.trim(),
            customBaseUrl = settings.customBaseUrl.trim(),
            customProviderName = settings.customProviderName.trim(),
            customDefaultModel = settings.customDefaultModel.trim(),
            assistantStyle = settings.assistantStyle.trim().ifEmpty { "professional" }
        )

        encryptedPrefs.edit()
            .putString(KEY_DEFAULT_MODEL, sanitized.defaultModel)
            .putString(KEY_OPENROUTER_KEY, sanitized.openrouterApiKey)
            .putString(KEY_ANTHROPIC_KEY, sanitized.anthropicApiKey)
            .putString(KEY_DEEPSEEK_KEY, sanitized.deepseekApiKey)
            .putString(KEY_OPENAI_KEY, sanitized.openaiApiKey)
            .putString(KEY_OLLAMA_URL, sanitized.ollamaBaseUrl)
            .putString(KEY_CUSTOM_API_KEY, sanitized.customApiKey)
            .putString(KEY_CUSTOM_BASE_URL, sanitized.customBaseUrl)
            .putString(KEY_CUSTOM_PROVIDER_NAME, sanitized.customProviderName)
            .putString(KEY_CUSTOM_DEFAULT_MODEL, sanitized.customDefaultModel)
            .putString(KEY_ASSISTANT_STYLE, sanitized.assistantStyle)
            .apply()  // 异步提交（不阻塞 UI 线程）

        // 更新内存中的 StateFlow，触发热重载
        _settings.value = sanitized

        // 注意：故意不打印 API Key 到日志，防止 logcat 泄露
        Log.i(TAG, "Settings saved. defaultModel=${sanitized.defaultModel}, " +
                "providers=[openrouter=${sanitized.openrouterApiKey.isNotEmpty()}, " +
                "anthropic=${sanitized.anthropicApiKey.isNotEmpty()}, " +
                "deepseek=${sanitized.deepseekApiKey.isNotEmpty()}, " +
                "openai=${sanitized.openaiApiKey.isNotEmpty()}, " +
                "ollama=${sanitized.ollamaBaseUrl.isNotEmpty()}, " +
                "custom=${sanitized.customApiKey.isNotEmpty()}]")
    }

    /**
     * 从加密存储加载配置
     */
    private fun load(): AppSettings {
        return AppSettings(
            defaultModel = encryptedPrefs.getString(KEY_DEFAULT_MODEL, "openrouter/anthropic/claude-3.5-sonnet")
                ?: "openrouter/anthropic/claude-3.5-sonnet",
            openrouterApiKey = encryptedPrefs.getString(KEY_OPENROUTER_KEY, "") ?: "",
            anthropicApiKey = encryptedPrefs.getString(KEY_ANTHROPIC_KEY, "") ?: "",
            deepseekApiKey = encryptedPrefs.getString(KEY_DEEPSEEK_KEY, "") ?: "",
            openaiApiKey = encryptedPrefs.getString(KEY_OPENAI_KEY, "") ?: "",
            ollamaBaseUrl = encryptedPrefs.getString(KEY_OLLAMA_URL, "http://192.168.1.x:11434")
                ?: "http://192.168.1.x:11434",
            customApiKey = encryptedPrefs.getString(KEY_CUSTOM_API_KEY, "") ?: "",
            customBaseUrl = encryptedPrefs.getString(KEY_CUSTOM_BASE_URL, "") ?: "",
            customProviderName = encryptedPrefs.getString(KEY_CUSTOM_PROVIDER_NAME, "") ?: "",
            customDefaultModel = encryptedPrefs.getString(KEY_CUSTOM_DEFAULT_MODEL, "") ?: "",
            assistantStyle = encryptedPrefs.getString(KEY_ASSISTANT_STYLE, "professional") ?: "professional"
        )
    }

    /**
     * 检查是否至少配置了一个可用的 Provider
     */
    fun hasAnyProvider(): Boolean {
        val s = _settings.value
        return s.openrouterApiKey.isNotEmpty()
                || s.anthropicApiKey.isNotEmpty()
                || s.deepseekApiKey.isNotEmpty()
                || s.openaiApiKey.isNotEmpty()
                || s.ollamaBaseUrl.isNotEmpty()
                || (s.customApiKey.isNotEmpty() && s.customBaseUrl.isNotEmpty())
    }

    companion object {
        // EncryptedSharedPreferences 文件名（不含敏感信息）
        private const val PREFS_FILE_NAME = "nanobot_secure_config"

        // SharedPreferences key 常量（key 本身也会被加密）
        private const val KEY_DEFAULT_MODEL = "default_model"
        private const val KEY_OPENROUTER_KEY = "openrouter_api_key"
        private const val KEY_ANTHROPIC_KEY = "anthropic_api_key"
        private const val KEY_DEEPSEEK_KEY = "deepseek_api_key"
        private const val KEY_OPENAI_KEY = "openai_api_key"
        private const val KEY_OLLAMA_URL = "ollama_base_url"
        private const val KEY_CUSTOM_API_KEY = "custom_api_key"
        private const val KEY_CUSTOM_BASE_URL = "custom_base_url"
        private const val KEY_CUSTOM_PROVIDER_NAME = "custom_provider_name"
        private const val KEY_CUSTOM_DEFAULT_MODEL = "custom_default_model"
        private const val KEY_ASSISTANT_STYLE = "assistant_style"

        @Volatile
        private var INSTANCE: SettingsRepository? = null

        /**
         * 获取单例实例（线程安全）
         */
        fun get(context: Context): SettingsRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SettingsRepository(context).also { INSTANCE = it }
            }
        }
    }
}
