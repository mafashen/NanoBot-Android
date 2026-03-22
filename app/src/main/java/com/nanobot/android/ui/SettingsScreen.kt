package com.nanobot.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nanobot.android.data.AppSettings

/**
 * 设置界面
 *
 * 安全说明：
 * - API Key 在界面上默认以掩码显示（PasswordVisualTransformation）
 * - 所有 Key 通过 SettingsRepository 保存到 EncryptedSharedPreferences
 * - 保存后立即热重载 Provider，无需重启 App
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val savedSettings by viewModel.settings.collectAsStateWithLifecycle()

    // 编辑中的临时状态（用户改完再保存，避免实时写入影响正在进行的对话）
    var defaultModel by remember(savedSettings.defaultModel) {
        mutableStateOf(savedSettings.defaultModel)
    }
    var openrouterKey by remember(savedSettings.openrouterApiKey) {
        mutableStateOf(savedSettings.openrouterApiKey)
    }
    var anthropicKey by remember(savedSettings.anthropicApiKey) {
        mutableStateOf(savedSettings.anthropicApiKey)
    }
    var deepseekKey by remember(savedSettings.deepseekApiKey) {
        mutableStateOf(savedSettings.deepseekApiKey)
    }
    var openaiKey by remember(savedSettings.openaiApiKey) {
        mutableStateOf(savedSettings.openaiApiKey)
    }
    var ollamaUrl by remember(savedSettings.ollamaBaseUrl) {
        mutableStateOf(savedSettings.ollamaBaseUrl)
    }
    var customApiKey by remember(savedSettings.customApiKey) {
        mutableStateOf(savedSettings.customApiKey)
    }
    var customBaseUrl by remember(savedSettings.customBaseUrl) {
        mutableStateOf(savedSettings.customBaseUrl)
    }
    var customProviderName by remember(savedSettings.customProviderName) {
        mutableStateOf(savedSettings.customProviderName)
    }
    var customDefaultModel by remember(savedSettings.customDefaultModel) {
        mutableStateOf(savedSettings.customDefaultModel)
    }
    var assistantStyle by remember(savedSettings.assistantStyle) {
        mutableStateOf(savedSettings.assistantStyle)
    }

    var saveSuccess by remember { mutableStateOf(false) }

    fun doSave() {
        viewModel.save(
            AppSettings(
                defaultModel = defaultModel,
                openrouterApiKey = openrouterKey,
                anthropicApiKey = anthropicKey,
                deepseekApiKey = deepseekKey,
                openaiApiKey = openaiKey,
                ollamaBaseUrl = ollamaUrl,
                customApiKey = customApiKey,
                customBaseUrl = customBaseUrl,
                customProviderName = customProviderName,
                customDefaultModel = customDefaultModel,
                assistantStyle = assistantStyle
            )
        )
        saveSuccess = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("模型配置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { doSave() }
                    ) {
                        Icon(
                            Icons.Default.Save,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("保存")
                    }
                }
            )
        }
    ) { padding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // ── 保存成功提示 ──────────────────────────────────────────────────
            if (saveSuccess) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "配置已保存，Provider 已重新加载（无需重启）",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            // ── 安全说明 ──────────────────────────────────────────────────────
            item {
                AssistantStyleCard(
                    currentStyle = assistantStyle,
                    onStyleChange = { assistantStyle = it }
                )
            }

            // ── 安全提示 ──────────────────────────────────────────────────────
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp).padding(top = 2.dp)
                        )
                        Text(
                            "API Key 使用 Android KeyStore 加密存储，其他 App 无法读取。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ── 默认模型 ──────────────────────────────────────────────────────
            item {
                SettingsSection(
                    title = "默认模型",
                    icon = Icons.Default.SmartToy
                ) {
                    OutlinedTextField(
                        value = defaultModel,
                        onValueChange = { defaultModel = it; saveSuccess = false },
                        label = { Text("模型标识") },
                        placeholder = { Text("openrouter/anthropic/claude-3.5-sonnet") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.ModelTraining, contentDescription = null)
                        }
                    )
                    Text(
                        "格式：provider/model，如 openrouter/anthropic/claude-3.5-sonnet、anthropic/claude-3-5-sonnet-20241022、deepseek-chat、ollama/qwen2.5",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            // ── OpenRouter ────────────────────────────────────────────────────
            item {
                ProviderSection(
                    title = "OpenRouter",
                    subtitle = "推荐 · 支持 Claude / GPT / Gemini / DeepSeek 等 100+ 模型",
                    isConfigured = openrouterKey.isNotEmpty()
                ) {
                    ApiKeyField(
                        value = openrouterKey,
                        onValueChange = { openrouterKey = it; saveSuccess = false },
                        label = "API Key",
                        placeholder = "sk-or-v1-..."
                    )
                    Text(
                        "常用模型：anthropic/claude-3.5-sonnet · openai/gpt-4o · google/gemini-2.0-flash",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            // ── Anthropic 直连 ────────────────────────────────────────────────
            item {
                ProviderSection(
                    title = "Anthropic（Claude 直连）",
                    subtitle = "直接调用 Claude API，支持 prompt caching",
                    isConfigured = anthropicKey.isNotEmpty()
                ) {
                    ApiKeyField(
                        value = anthropicKey,
                        onValueChange = { anthropicKey = it; saveSuccess = false },
                        label = "API Key",
                        placeholder = "sk-ant-api03-..."
                    )
                    Text(
                        "常用模型：claude-3-5-sonnet-20241022 · claude-3-5-haiku-20241022",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            // ── DeepSeek ──────────────────────────────────────────────────────
            item {
                ProviderSection(
                    title = "DeepSeek",
                    subtitle = "支持 deepseek-chat（V3）和 deepseek-reasoner（R1）",
                    isConfigured = deepseekKey.isNotEmpty()
                ) {
                    ApiKeyField(
                        value = deepseekKey,
                        onValueChange = { deepseekKey = it; saveSuccess = false },
                        label = "API Key",
                        placeholder = "sk-..."
                    )
                    Text(
                        "常用模型：deepseek-chat · deepseek-reasoner",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            // ── OpenAI ────────────────────────────────────────────────────────
            item {
                ProviderSection(
                    title = "OpenAI",
                    subtitle = "GPT-4o / o1 / o3-mini 等",
                    isConfigured = openaiKey.isNotEmpty()
                ) {
                    ApiKeyField(
                        value = openaiKey,
                        onValueChange = { openaiKey = it; saveSuccess = false },
                        label = "API Key",
                        placeholder = "sk-proj-..."
                    )
                    Text(
                        "常用模型：gpt-4o · gpt-4o-mini · o1 · o3-mini",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            // ── Ollama / 本地模型 ─────────────────────────────────────────────
            item {
                ProviderSection(
                    title = "Ollama（本地 / NAS）",
                    subtitle = "运行在局域网 NAS 或本地的 Ollama 服务，无需 API Key",
                    isConfigured = ollamaUrl.isNotEmpty() && !ollamaUrl.contains("192.168.1.x")
                ) {
                    OutlinedTextField(
                        value = ollamaUrl,
                        onValueChange = { ollamaUrl = it; saveSuccess = false },
                        label = { Text("Ollama 服务地址") },
                        placeholder = { Text("http://192.168.1.100:11434") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        leadingIcon = {
                            Icon(Icons.Default.Dns, contentDescription = null)
                        }
                    )
                    Text(
                        "模型前缀 ollama/，如：ollama/qwen2.5 · ollama/llama3.2 · ollama/deepseek-r1",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            // ── 自定义 OpenAI 兼容接口 ────────────────────────────────────────
            item {
                ProviderSection(
                    title = "自定义接口（OpenAI 兼容）",
                    subtitle = "任意兼容 OpenAI Chat Completions API 的服务",
                    isConfigured = customApiKey.isNotEmpty() && customBaseUrl.isNotEmpty()
                ) {
                    OutlinedTextField(
                        value = customBaseUrl,
                        onValueChange = { customBaseUrl = it; saveSuccess = false },
                        label = { Text("API Base URL") },
                        placeholder = { Text("https://your-api.example.com/v1") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        leadingIcon = {
                            Icon(Icons.Default.Language, contentDescription = null)
                        }
                    )
                    ApiKeyField(
                        value = customApiKey,
                        onValueChange = { customApiKey = it; saveSuccess = false },
                        label = "API Key",
                        placeholder = "your-api-key"
                    )
                    OutlinedTextField(
                        value = customProviderName,
                        onValueChange = { customProviderName = it; saveSuccess = false },
                        label = { Text("Provider 标识（用于模型名前缀）") },
                        placeholder = { Text("custom") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = customDefaultModel,
                        onValueChange = { customDefaultModel = it; saveSuccess = false },
                        label = { Text("默认模型名") },
                        placeholder = { Text("gpt-3.5-turbo") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Text(
                        "使用时：在「默认模型」填入 {Provider标识}/{模型名}，如 custom/gpt-3.5-turbo",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            // ── 底部保存按钮 ──────────────────────────────────────────────────
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { doSave() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("保存配置")
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// 子组件
// ────────────────────────────────────────────────────────────────────────────

@Composable
fun SettingsSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.Settings,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            content()
        }
    }
}

/**
 * Provider 配置分组卡片，支持展开/折叠
 */
@Composable
fun ProviderSection(
    title: String,
    subtitle: String,
    isConfigured: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by remember { mutableStateOf(isConfigured) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 标题行（点击展开/折叠）
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (isConfigured) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "已配置",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "收起" else "展开"
                    )
                }
            }

            // 可展开内容
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    content = content
                )
            }
        }
    }
}

/**
 * 助手风格选择卡片
 *
 * 提供两种风格：活泼俏皮 / 严谨专业
 * 选择后保存到 EncryptedSharedPreferences，下一次对话立即生效（热重载）
 */
@Composable
fun AssistantStyleCard(
    currentStyle: String,
    onStyleChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Face,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    "助手风格",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                "选择不同风格将调整 NanoBot 的说话方式和系统提示词，下次对话立即生效。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StyleOptionCard(
                    emoji = "🎉",
                    title = "活泼俏皮",
                    description = "轻松有趣，喜欢用 emoji，像朋友一样聊天",
                    selected = currentStyle == "lively",
                    onClick = { onStyleChange("lively") },
                    modifier = Modifier.weight(1f)
                )
                StyleOptionCard(
                    emoji = "📋",
                    title = "严谨专业",
                    description = "简洁精准，结构清晰，适合工作和技术场景",
                    selected = currentStyle == "professional",
                    onClick = { onStyleChange("professional") },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StyleOptionCard(
    emoji: String,
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (selected)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)

    OutlinedCard(
        onClick = onClick,
        modifier = modifier,
        border = androidx.compose.foundation.BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = borderColor
        ),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (selected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(emoji, style = MaterialTheme.typography.headlineMedium)
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onSurface
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = if (selected)
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                else
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            if (selected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "已选择",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun ApiKeyField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    var showKey by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        leadingIcon = {
            Icon(Icons.Default.Key, contentDescription = null)
        },
        trailingIcon = {
            IconButton(onClick = { showKey = !showKey }) {
                Icon(
                    imageVector = if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (showKey) "隐藏 Key" else "显示 Key"
                )
            }
        }
    )
}
