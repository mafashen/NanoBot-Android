package com.nanobot.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nanobot.android.bus.AgentLogEntry
import com.nanobot.android.bus.AgentLogLevel
import com.nanobot.android.bus.MessageBus
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Agent 运行日志面板（底部抽屉式）
 *
 * 展示 AgentLoop 的实时运行日志，支持：
 * - 按日志级别着色（INFO=白、DEBUG=蓝、WARN=黄、ERROR=红）
 * - 自动滚动到最新条目
 * - 一键清空日志（仅清空 UI，不影响 logcat）
 * - 滑入/滑出动画
 */
@Composable
fun AgentLogPanel(
    visible: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 收集日志条目
    val logEntries = remember { mutableStateListOf<AgentLogEntry>() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // 订阅日志流（回放历史 + 实时追加）
    LaunchedEffect(Unit) {
        MessageBus.agentLogFlow.collect { entry ->
            logEntries.add(entry)
            // 保留最新 500 条，防止内存溢出
            if (logEntries.size > 500) {
                logEntries.removeAt(0)
            }
        }
    }

    // 新日志到来自动滚到底
    LaunchedEffect(logEntries.size) {
        if (logEntries.isNotEmpty()) {
            scope.launch {
                listState.animateScrollToItem(logEntries.size - 1)
            }
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.45f),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            color = Color(0xFF1A1A2E),
            shadowElevation = 12.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 标题栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "运行日志",
                        color = Color(0xFF00FF9F),
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${logEntries.size} 条",
                        color = Color(0xFF888888),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    // 清空按钮
                    IconButton(
                        onClick = { logEntries.clear() },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "清空日志",
                            tint = Color(0xFF888888),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    // 关闭按钮
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭",
                            tint = Color(0xFF888888),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                HorizontalDivider(color = Color(0xFF333355))

                // 日志列表
                if (logEntries.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "暂无日志，开始对话后将显示运行信息",
                            color = Color(0xFF555577),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        items(logEntries) { entry ->
                            LogEntryRow(entry)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogEntryRow(entry: AgentLogEntry) {
    val timeStr = remember(entry.timestamp) {
        SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(entry.timestamp))
    }
    val (levelTag, levelColor) = when (entry.level) {
        AgentLogLevel.VERBOSE -> "V" to Color(0xFF888888)
        AgentLogLevel.DEBUG   -> "D" to Color(0xFF5599FF)
        AgentLogLevel.INFO    -> "I" to Color(0xFFCCCCCC)
        AgentLogLevel.WARN    -> "W" to Color(0xFFFFCC00)
        AgentLogLevel.ERROR   -> "E" to Color(0xFFFF4444)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (entry.level == AgentLogLevel.ERROR)
                    Color(0xFF2A0000)
                else
                    Color.Transparent,
                RoundedCornerShape(2.dp)
            )
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        // 时间戳
        Text(
            text = timeStr,
            color = Color(0xFF555577),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.width(6.dp))
        // 级别标签
        Text(
            text = levelTag,
            color = levelColor,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(10.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        // TAG
        Text(
            text = "${entry.tag}: ",
            color = Color(0xFF7799BB),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
        // 消息内容
        Text(
            text = entry.message,
            color = levelColor,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f)
        )
    }
}
