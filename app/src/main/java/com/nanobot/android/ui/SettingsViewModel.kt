package com.nanobot.android.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.nanobot.android.data.AppSettings
import com.nanobot.android.data.SettingsRepository
import kotlinx.coroutines.flow.StateFlow

/**
 * SettingsViewModel - 设置页面的 ViewModel
 *
 * 使用 AndroidViewModel 以便访问 Application Context（用于初始化 SettingsRepository）。
 * SettingsRepository 的 StateFlow 会通知 NanoBotApplication 热重载 Provider。
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SettingsRepository.get(application)

    /** 当前设置的 StateFlow，UI 可直接 collectAsState() */
    val settings: StateFlow<AppSettings> = repository.settings

    /**
     * 保存新配置。
     * 会立即更新 StateFlow，AgentLoop 通过监听该 Flow 自动热重载 Provider，无需重启 App。
     */
    fun save(settings: AppSettings) {
        repository.save(settings)
    }

    /** 是否已配置至少一个 Provider */
    fun hasAnyProvider(): Boolean = repository.hasAnyProvider()
}
