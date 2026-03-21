# NanoBot Android

基于 NanoBot 项目的 Android 原生应用，使用 Kotlin + Jetpack Compose 开发。

## 项目结构

```
app/src/main/java/com/nanobot/android/
├── NanoBotApplication.kt          # Application 类，初始化所有组件
├── agent/
│   ├── AgentLoop.kt               # 核心处理引擎（对应 NanoBot agent/loop.py）
│   ├── ContextBuilder.kt          # 上下文构建（对应 agent/context.py）
│   └── MemoryStore.kt             # 记忆系统（对应 agent/memory.py）
├── bus/
│   └── MessageBus.kt              # 消息总线（对应 bus/queue.py）
├── model/
│   └── Models.kt                  # 所有数据模型
├── provider/
│   ├── LLMProvider.kt             # Provider 抽象基类
│   └── Providers.kt               # OpenRouter/Anthropic/DeepSeek/OpenAI 实现
├── session/
│   └── SessionManager.kt          # 会话管理（对应 session/manager.py）
├── service/
│   └── AgentForegroundService.kt  # 前台服务 + 开机自启
├── tools/
│   └── Tools.kt                   # 工具系统（ToolRegistry + ToolExecutor）
└── ui/
    ├── MainActivity.kt             # 主界面
    ├── ChatScreen.kt              # 聊天界面（Compose）
    ├── ChatViewModel.kt           # ViewModel
    ├── SettingsScreen.kt          # 设置界面
    └── theme/
        └── Theme.kt               # Material3 主题
```

## 功能特性

- ✅ **多 LLM Provider 支持**：OpenRouter（100+ 模型）、Anthropic Claude、DeepSeek、OpenAI、Ollama（本地模型）
- ✅ **Agent Loop**：最多 40 次迭代，支持工具调用链
- ✅ **双层记忆系统**：MEMORY.md（长期记忆）+ HISTORY.md（对话摘要）
- ✅ **会话持久化**：JSONL 格式，支持多会话
- ✅ **工具系统**：文件读写、Web 搜索（DuckDuckGo）、Web 获取、记忆管理
- ✅ **思维链支持**：DeepSeek R1、Claude Extended Thinking
- ✅ **前台服务**：后台持续运行，开机自启动
- ✅ **Jetpack Compose UI**：Material3 设计，深色/浅色主题

## 快速开始

### 1. 配置 API Key

打开 Android Studio，进入设置界面配置以下任一 API Key：

- **OpenRouter**（推荐）：访问 https://openrouter.ai 获取
- **Anthropic**：访问 https://console.anthropic.com 获取
- **DeepSeek**：访问 https://platform.deepseek.com 获取

### 2. 配置 Ollama（可选，用于本地模型）

在 NAS 或服务器上安装 Ollama：
```bash
curl -fsSL https://ollama.ai/install.sh | sh
ollama serve
ollama pull llama3.2
```

在设置界面填入 Ollama 地址：`http://你的NAS-IP:11434`

### 3. 编译运行

```bash
cd NanoBot-Android
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

## 系统命令

| 命令 | 功能 |
|------|------|
| `/new` | 开始新对话 |
| `/memory` | 查看当前记忆 |
| `/clear` | 清空记忆 |
| `/session` | 查看当前会话 ID |
| `/help` | 显示帮助 |

## 与 NanoBot 原版的对应关系

| NanoBot Python 模块 | Android Kotlin 模块 |
|---------------------|---------------------|
| `agent/loop.py` | `agent/AgentLoop.kt` |
| `agent/context.py` | `agent/ContextBuilder.kt` |
| `agent/memory.py` | `agent/MemoryStore.kt` |
| `bus/queue.py` | `bus/MessageBus.kt` |
| `session/manager.py` | `session/SessionManager.kt` |
| `providers/base.py` | `provider/LLMProvider.kt` |
| `providers/registry.py` + 各实现 | `provider/Providers.kt` |
| `tools/*.py` | `tools/Tools.kt` |

## GitHub Actions 云编译

项目已配置 GitHub Actions CI，**每次推送到 GitHub 自动编译 APK**，无需本地环境。

### 使用方法

```bash
# 1. 创建 GitHub 仓库（替换 YOUR_USERNAME）
cd NanoBot-Android
git init
git add .
git commit -m "Initial NanoBot Android"
git remote add origin https://github.com/YOUR_USERNAME/NanoBot-Android.git
git push -u origin master

# 2. 访问 Actions 页面
#    https://github.com/YOUR_USERNAME/NanoBot-Android/actions

# 3. 等待编译完成，点击 "android-ci" → Artifacts → 下载 app-debug.apk
```

### 编译产物
- `app/build/outputs/apk/debug/app-debug.apk` → 安装到 vivo 手机即可

---

## 待实现功能

- [ ] 图片/语音输入
- [ ] 定时任务（对应 NanoBot cron/service.py）
- [ ] SubAgent（后台并行任务）
- [ ] 技能系统（对应 agent/skills.py）
- [ ] Markdown 完整渲染（集成 Markwon）
- [ ] 导出对话记录
- [ ] Widget 桌面小部件
