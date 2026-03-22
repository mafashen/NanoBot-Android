package com.nanobot.android.tools

import android.util.Log
import com.nanobot.android.model.*

private const val TAG = "ToolRegistry"

/**
 * ToolRegistry - 工具注册表
 *
 * 对应 NanoBot 的 tools/registry.py
 */
class ToolRegistry {

    private val tools = mutableMapOf<String, ToolDefinition>()

    fun register(tool: ToolDefinition) {
        tools[tool.name] = tool
        Log.d(TAG, "Tool registered: ${tool.name}")
    }

    fun get(name: String): ToolDefinition? = tools[name]

    fun getAll(): List<ToolDefinition> = tools.values.toList()

    fun isRegistered(name: String): Boolean = name in tools

    /**
     * 注册所有默认工具
     *
     * 对应 NanoBot 的 AgentLoop._register_default_tools()
     */
    fun registerDefaultTools(config: ToolsConfig) {
        // 文件读取工具
        if (config.fileReadEnabled) {
            register(ToolDefinition(
                name = "read_file",
                description = "读取指定文件的内容。只能读取工作空间内的文件。",
                parameters = ToolParameters(
                    properties = mapOf(
                        "path" to ToolParameterProperty(
                            type = "string",
                            description = "要读取的文件路径（相对于工作空间）"
                        ),
                        "offset" to ToolParameterProperty(
                            type = "integer",
                            description = "起始行号（可选，从0开始）"
                        ),
                        "limit" to ToolParameterProperty(
                            type = "integer",
                            description = "读取的最大行数（可选）"
                        )
                    ),
                    required = listOf("path")
                )
            ))
        }

        // 文件写入工具
        if (config.fileWriteEnabled) {
            register(ToolDefinition(
                name = "write_file",
                description = "写入内容到指定文件。只能写入工作空间内的文件。",
                parameters = ToolParameters(
                    properties = mapOf(
                        "path" to ToolParameterProperty(
                            type = "string",
                            description = "要写入的文件路径（相对于工作空间）"
                        ),
                        "content" to ToolParameterProperty(
                            type = "string",
                            description = "要写入的内容"
                        ),
                        "append" to ToolParameterProperty(
                            type = "boolean",
                            description = "是否追加模式（默认覆盖）"
                        )
                    ),
                    required = listOf("path", "content")
                )
            ))

            register(ToolDefinition(
                name = "list_files",
                description = "列出工作空间中的文件和目录。",
                parameters = ToolParameters(
                    properties = mapOf(
                        "directory" to ToolParameterProperty(
                            type = "string",
                            description = "要列出的目录路径（相对于工作空间，可选）"
                        )
                    )
                )
            ))
        }

        // Web 搜索工具
        if (config.webSearchEnabled) {
            register(ToolDefinition(
                name = "web_search",
                description = "使用搜索引擎搜索网络信息，返回搜索结果摘要。",
                parameters = ToolParameters(
                    properties = mapOf(
                        "query" to ToolParameterProperty(
                            type = "string",
                            description = "搜索查询词"
                        ),
                        "num_results" to ToolParameterProperty(
                            type = "integer",
                            description = "返回结果数量（默认5）"
                        )
                    ),
                    required = listOf("query")
                )
            ))
        }

        // Web 获取工具
        if (config.webFetchEnabled) {
            register(ToolDefinition(
                name = "web_fetch",
                description = "获取指定 URL 的网页内容，转换为纯文本格式。",
                parameters = ToolParameters(
                    properties = mapOf(
                        "url" to ToolParameterProperty(
                            type = "string",
                            description = "要获取的网页 URL（必须是 http/https）"
                        ),
                        "extract_text" to ToolParameterProperty(
                            type = "boolean",
                            description = "是否仅提取纯文本（默认 true）"
                        )
                    ),
                    required = listOf("url")
                )
            ))
        }

        // 记忆管理工具
        register(ToolDefinition(
            name = "save_memory",
            description = "将重要信息保存到长期记忆中。用于记录用户偏好、重要事实等。",
            parameters = ToolParameters(
                properties = mapOf(
                    "content" to ToolParameterProperty(
                        type = "string",
                        description = "要保存的内容"
                    )
                ),
                required = listOf("content")
            )
        ))

        register(ToolDefinition(
            name = "read_memory",
            description = "读取长期记忆内容。",
            parameters = ToolParameters(
                properties = emptyMap()
            )
        ))

        Log.i(TAG, "Default tools registered: ${tools.keys.joinToString(", ")}")
    }
}

/**
 * ToolExecutor - 工具执行器
 *
 * 对应 NanoBot 的各个 tools/xxx.py 实现
 */
class ToolExecutor(
    private val memoryStore: com.nanobot.android.agent.MemoryStore,
    private val config: ToolsConfig = ToolsConfig()
) {

    private val httpClient = okhttp3.OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    /**
     * 执行工具调用
     */
    suspend fun execute(toolName: String, arguments: Map<String, Any>): String {
        return when (toolName) {
            "read_file" -> executeReadFile(arguments)
            "write_file" -> executeWriteFile(arguments)
            "list_files" -> executeListFiles(arguments)
            "web_search" -> executeWebSearch(arguments)
            "web_fetch" -> executeWebFetch(arguments)
            "save_memory" -> executeSaveMemory(arguments)
            "read_memory" -> executeReadMemory(arguments)
            else -> throw IllegalArgumentException("未知工具: $toolName")
        }
    }

    private fun executeReadFile(args: Map<String, Any>): String {
        val path = args["path"] as? String ?: throw IllegalArgumentException("缺少 path 参数")
        val offset = (args["offset"] as? String)?.toIntOrNull() ?: 0
        val limit = (args["limit"] as? String)?.toIntOrNull()

        val content = memoryStore.readFile(path)
            ?: return "错误：文件不存在: $path"

        val lines = content.lines()
        val selectedLines = if (limit != null) {
            lines.drop(offset).take(limit)
        } else {
            lines.drop(offset)
        }

        return selectedLines.joinToString("\n")
    }

    private fun executeWriteFile(args: Map<String, Any>): String {
        if (!config.fileWriteEnabled) return "错误：文件写入已禁用"
        val path = args["path"] as? String ?: throw IllegalArgumentException("缺少 path 参数")
        val content = args["content"] as? String ?: throw IllegalArgumentException("缺少 content 参数")

        memoryStore.writeFile(path, content)
        return "文件已写入: $path（${content.length} 字符）"
    }

    private fun executeListFiles(args: Map<String, Any>): String {
        val directory = args["directory"] as? String ?: ""
        val files = memoryStore.listFiles(directory)
        return if (files.isEmpty()) {
            "（目录为空或不存在）"
        } else {
            files.joinToString("\n")
        }
    }

    private suspend fun executeWebSearch(args: Map<String, Any>): String {
        val query = args["query"] as? String ?: throw IllegalArgumentException("缺少 query 参数")
        val numResults = (args["num_results"] as? String)?.toIntOrNull() ?: 5

        // 使用 DuckDuckGo 搜索 API（无需 API Key）
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "https://html.duckduckgo.com/html/?q=$encodedQuery"

        return try {
            val request = okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android 14; Mobile)")
                .build()
            val response = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                httpClient.newCall(request).execute()
            }
            val html = response.body?.string() ?: return "搜索失败：空响应"
            extractSearchResults(html, numResults)
        } catch (e: Exception) {
            "搜索失败：${e.message}"
        }
    }

    private fun extractSearchResults(html: String, maxResults: Int): String {
        // 简单的 HTML 解析，提取搜索结果
        val results = mutableListOf<String>()
        val pattern = Regex("""<a class="result__a"[^>]*href="([^"]+)"[^>]*>([^<]+)</a>""")
        val snippetPattern = Regex("""<a class="result__snippet"[^>]*>([^<]+)</a>""")

        val links = pattern.findAll(html).take(maxResults).toList()
        val snippets = snippetPattern.findAll(html).toList()

        links.forEachIndexed { i, match ->
            val url = match.groupValues[1]
            val title = match.groupValues[2].trim()
            val snippet = snippets.getOrNull(i)?.groupValues?.get(1)?.trim() ?: ""
            results.add("${i + 1}. **$title**\n   $url\n   $snippet")
        }

        return if (results.isEmpty()) {
            "未找到搜索结果"
        } else {
            "搜索结果：\n\n${results.joinToString("\n\n")}"
        }
    }

    private suspend fun executeWebFetch(args: Map<String, Any>): String {
        val url = args["url"] as? String ?: throw IllegalArgumentException("缺少 url 参数")

        // 基本 URL 安全验证
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return "错误：只支持 http/https URL"
        }

        return try {
            val request = okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android 14; Mobile)")
                .build()
            val response = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                httpClient.newCall(request).execute()
            }

            if (!response.isSuccessful) {
                return "获取失败：HTTP ${response.code}"
            }

            val html = response.body?.string() ?: return "获取失败：空响应"
            val text = extractTextFromHtml(html)
            text.take(8000)  // 限制长度
        } catch (e: Exception) {
            "获取失败：${e.message}"
        }
    }

    private fun extractTextFromHtml(html: String): String {
        // 移除 script 和 style 标签
        var text = html
            .replace(Regex("<script[^>]*>[\\s\\S]*?</script>"), "")
            .replace(Regex("<style[^>]*>[\\s\\S]*?</style>"), "")
            .replace(Regex("<[^>]+>"), " ")  // 移除所有 HTML 标签
            .replace(Regex("\\s+"), " ")     // 合并空白
            .replace(Regex("&amp;"), "&")
            .replace(Regex("&lt;"), "<")
            .replace(Regex("&gt;"), ">")
            .replace(Regex("&quot;"), "\"")
            .replace(Regex("&nbsp;"), " ")
            .trim()
        return text
    }

    private fun executeSaveMemory(args: Map<String, Any>): String {
        val content = args["content"] as? String ?: throw IllegalArgumentException("缺少 content 参数")
        memoryStore.appendToMemory(content)
        return "记忆已保存"
    }

    private fun executeReadMemory(args: Map<String, Any>): String {
        val memory = memoryStore.readMemory()
        return if (memory.isEmpty()) "（暂无记忆）" else memory
    }
}
