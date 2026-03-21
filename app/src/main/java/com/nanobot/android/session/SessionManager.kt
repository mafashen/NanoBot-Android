package com.nanobot.android.session

import android.content.Context
import android.util.Log
import com.nanobot.android.model.ChatMessage
import com.nanobot.android.model.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

private const val TAG = "SessionManager"

/**
 * SessionManager - 会话持久化管理
 *
 * 对应 NanoBot 的 session/manager.py
 *
 * 使用 JSONL 文件格式（每行一个 JSON 消息）
 * 支持：
 * - 多会话管理
 * - 会话内存缓存
 * - 消息历史获取
 * - 整合边界管理
 */
class SessionManager(context: Context) {

    private val sessionsDir = File(context.filesDir, "sessions")
    private val sessionCache = mutableMapOf<String, Session>()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    init {
        sessionsDir.mkdirs()
        Log.i(TAG, "SessionManager initialized at ${sessionsDir.absolutePath}")
    }

    /**
     * 获取或创建会话
     */
    fun getOrCreate(sessionKey: String): Session {
        return sessionCache.getOrPut(sessionKey) {
            loadFromDisk(sessionKey) ?: Session(key = sessionKey)
        }
    }

    /**
     * 获取会话（不创建）
     */
    fun get(sessionKey: String): Session? {
        return sessionCache[sessionKey] ?: loadFromDisk(sessionKey)
    }

    /**
     * 添加消息到会话
     */
    fun addMessage(sessionKey: String, message: ChatMessage) {
        val session = getOrCreate(sessionKey)
        session.messages.add(message)

        // 异步持久化
        appendMessageToDisk(sessionKey, message)
        Log.d(TAG, "Message added to session $sessionKey: role=${message.role}, content=${message.content?.take(50)}")
    }

    /**
     * 获取会话历史消息
     */
    fun getMessages(sessionKey: String): List<ChatMessage> {
        return getOrCreate(sessionKey).messages.toList()
    }

    /**
     * 更新整合索引
     */
    fun updateConsolidatedIndex(sessionKey: String, index: Int) {
        val session = getOrCreate(sessionKey)
        // Session 是 data class，messages 是可变的，但需要特殊处理 lastConsolidatedIndex
        // 这里直接更新缓存中的 session（通过替换）
        sessionCache[sessionKey] = session.copy(lastConsolidatedIndex = index)
        Log.d(TAG, "Session $sessionKey consolidated index updated to $index")
    }

    /**
     * 列出所有会话
     */
    fun listSessions(): List<String> {
        return sessionsDir.listFiles()
            ?.filter { it.extension == "jsonl" }
            ?.map { it.nameWithoutExtension }
            ?: emptyList()
    }

    /**
     * 删除会话
     */
    fun deleteSession(sessionKey: String) {
        sessionCache.remove(sessionKey)
        File(sessionsDir, "$sessionKey.jsonl").delete()
        Log.i(TAG, "Session $sessionKey deleted")
    }

    /**
     * 清除所有会话
     */
    fun clearAllSessions() {
        sessionCache.clear()
        sessionsDir.listFiles()?.forEach { it.delete() }
        Log.i(TAG, "All sessions cleared")
    }

    // ==================== 磁盘 I/O ====================

    private fun getSessionFile(sessionKey: String): File {
        return File(sessionsDir, "$sessionKey.jsonl")
    }

    private fun loadFromDisk(sessionKey: String): Session? {
        val file = getSessionFile(sessionKey)
        if (!file.exists()) return null

        try {
            val messages = mutableListOf<ChatMessage>()
            file.forEachLine { line ->
                if (line.isBlank()) return@forEachLine
                try {
                    val message = json.decodeFromString<ChatMessage>(line)
                    messages.add(message)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse message line: ${e.message}")
                }
            }
            Log.i(TAG, "Loaded session $sessionKey: ${messages.size} messages")
            return Session(key = sessionKey, messages = messages)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load session $sessionKey: ${e.message}", e)
            return null
        }
    }

    private fun appendMessageToDisk(sessionKey: String, message: ChatMessage) {
        try {
            val file = getSessionFile(sessionKey)
            val line = json.encodeToString(message) + "\n"
            file.appendText(line)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist message: ${e.message}", e)
        }
    }

    /**
     * 重写整个会话到磁盘（用于整合后）
     */
    private fun rewriteToDisk(sessionKey: String, messages: List<ChatMessage>) {
        try {
            val file = getSessionFile(sessionKey)
            val content = messages.joinToString("\n") { json.encodeToString(it) } + "\n"
            file.writeText(content)
            Log.i(TAG, "Session $sessionKey rewritten: ${messages.size} messages")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to rewrite session: ${e.message}", e)
        }
    }
}
