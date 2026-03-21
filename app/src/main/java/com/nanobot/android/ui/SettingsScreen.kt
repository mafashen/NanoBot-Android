package com.nanobot.android.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/**
 * 设置界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("nanobot_config", Context.MODE_PRIVATE) }

    var openrouterKey by remember { mutableStateOf(prefs.getString("openrouter_api_key", "") ?: "") }
    var anthropicKey by remember { mutableStateOf(prefs.getString("anthropic_api_key", "") ?: "") }
    var deepseekKey by remember { mutableStateOf(prefs.getString("deepseek_api_key", "") ?: "") }
    var openaiKey by remember { mutableStateOf(prefs.getString("openai_api_key", "") ?: "") }
    var ollamaUrl by remember { mutableStateOf(prefs.getString("ollama_base_url", "http://192.168.1.x:11434") ?: "") }
    var defaultModel by remember { mutableStateOf(prefs.getString("default_model", "openrouter/anthropic/claude-3.5-sonnet") ?: "") }

    var showRestartDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        // 保存配置
                        prefs.edit()
                            .putString("openrouter_api_key", openrouterKey)
                            .putString("anthropic_api_key", anthropicKey)
                            .putString("deepseek_api_key", deepseekKey)
                            .putString("openai_api_key", openaiKey)
                            .putString("ollama_base_url", ollamaUrl)
                            .putString("default_model", defaultModel)
                            .apply()
                        showRestartDialog = true
                    }) {
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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                SettingsSection("默认模型") {
                    OutlinedTextField(
                        value = defaultModel,
                        onValueChange = { defaultModel = it },
                        label = { Text("模型名称") },
                        placeholder = { Text("openrouter/anthropic/claude-3.5-sonnet") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Text(
                        text = "格式: provider/model（如 openrouter/anthropic/claude-3.5-sonnet）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            item {
                SettingsSection("OpenRouter（推荐，支持 100+ 模型）") {
                    ApiKeyField(
                        value = openrouterKey,
                        onValueChange = { openrouterKey = it },
                        label = "API Key",
                        placeholder = "sk-or-..."
                    )
                    OutlinedButton(
                        onClick = { /* 打开 openrouter.ai */ },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("获取 OpenRouter API Key →")
                    }
                }
            }

            item {
                SettingsSection("Anthropic（Claude 直连）") {
                    ApiKeyField(
                        value = anthropicKey,
                        onValueChange = { anthropicKey = it },
                        label = "API Key",
                        placeholder = "sk-ant-..."
                    )
                }
            }

            item {
                SettingsSection("DeepSeek") {
                    ApiKeyField(
                        value = deepseekKey,
                        onValueChange = { deepseekKey = it },
                        label = "API Key",
                        placeholder = "sk-..."
                    )
                }
            }

            item {
                SettingsSection("OpenAI") {
                    ApiKeyField(
                        value = openaiKey,
                        onValueChange = { openaiKey = it },
                        label = "API Key",
                        placeholder = "sk-..."
                    )
                }
            }

            item {
                SettingsSection("Ollama（本地/NAS 上的模型）") {
                    OutlinedTextField(
                        value = ollamaUrl,
                        onValueChange = { ollamaUrl = it },
                        label = { Text("Ollama 服务地址") },
                        placeholder = { Text("http://192.168.1.x:11434") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Text(
                        text = "支持局域网 NAS 上的 Ollama 服务，无需 API Key",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }

    if (showRestartDialog) {
        AlertDialog(
            onDismissRequest = { showRestartDialog = false },
            title = { Text("配置已保存") },
            text = { Text("新的配置将在重启应用后生效。") },
            confirmButton = {
                TextButton(onClick = {
                    showRestartDialog = false
                    onBack()
                }) {
                    Text("确定")
                }
            }
        )
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            content()
        }
    }
}

@Composable
fun ApiKeyField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String
) {
    var showKey by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = { showKey = !showKey }) {
                Icon(
                    imageVector = if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (showKey) "隐藏" else "显示"
                )
            }
        }
    )
}
