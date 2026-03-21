package com.nanobot.android.agent

import android.content.Context
import android.util.Log
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val TAG = "MemoryStore"

/**
 * MemoryStore - 双层记忆系统
 *
 * 对应 NanoBot 的 agent/memory.py
 *
 * 实现：
 * - MEMORY.md：长期事实记忆（用户偏好、重要信息）
 * - HISTORY.md：可搜索的历史日志（对话摘要）
 */
class MemoryStore(context: Context) {

    private val workspaceDir: File = File(context.filesDir, "workspace")
    private val memoryFile: File = File(workspaceDir, "MEMORY.md")
    private val historyFile: File = File(workspaceDir, "HISTORY.md")

    init {
        workspaceDir.mkdirs()
        if (!memoryFile.exists()) {
            memoryFile.createNewFile()
            memoryFile.writeText("# 长期记忆\n\n这里存储重要的用户偏好和事实信息。\n")
        }
        if (!historyFile.exists()) {
            historyFile.createNewFile()
            historyFile.writeText("# 对话历史摘要\n\n")
        }
    }

    /**
     * 读取长期记忆内容
     */
    fun readMemory(): String {
        return if (memoryFile.exists()) {
            memoryFile.readText().trim()
        } else {
            ""
        }
    }

    /**
     * 写入/追加长期记忆
     */
    fun writeMemory(content: String) {
        memoryFile.writeText(content)
        Log.d(TAG, "Memory written: ${content.take(100)}")
    }

    /**
     * 追加到长期记忆
     */
    fun appendToMemory(content: String) {
        val existing = readMemory()
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        val newContent = if (existing.isEmpty()) {
            "# 长期记忆\n\n[$timestamp]\n$content\n"
        } else {
            "$existing\n\n[$timestamp]\n$content\n"
        }
        writeMemory(newContent)
    }

    /**
     * 读取历史摘要
     */
    fun readHistory(): String {
        return if (historyFile.exists()) {
            historyFile.readText().trim()
        } else {
            ""
        }
    }

    /**
     * 追加到历史摘要
     */
    fun appendToHistory(summary: String) {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        val entry = "\n\n## $timestamp\n\n$summary"
        historyFile.appendText(entry)
        Log.d(TAG, "History appended: ${summary.take(100)}")
    }

    /**
     * 清空记忆
     */
    fun clearMemory() {
        memoryFile.writeText("# 长期记忆\n\n（已清空）\n")
        Log.i(TAG, "Memory cleared")
    }

    /**
     * 清空历史
     */
    fun clearHistory() {
        historyFile.writeText("# 对话历史摘要\n\n（已清空）\n")
        Log.i(TAG, "History cleared")
    }

    /**
     * 获取工作空间路径
     */
    fun getWorkspacePath(): String = workspaceDir.absolutePath

    /**
     * 读取工作空间中的文件
     */
    fun readFile(relativePath: String): String? {
        val file = File(workspaceDir, relativePath)
        return if (file.exists() && file.isFile) {
            file.readText()
        } else {
            null
        }
    }

    /**
     * 写入工作空间中的文件
     */
    fun writeFile(relativePath: String, content: String) {
        val file = File(workspaceDir, relativePath)
        file.parentFile?.mkdirs()
        file.writeText(content)
    }

    /**
     * 列出工作空间文件
     */
    fun listFiles(subDir: String = ""): List<String> {
        val dir = if (subDir.isEmpty()) workspaceDir else File(workspaceDir, subDir)
        return if (dir.exists()) {
            dir.walkTopDown()
                .filter { it.isFile }
                .map { it.relativeTo(workspaceDir).path }
                .toList()
        } else {
            emptyList()
        }
    }
}
