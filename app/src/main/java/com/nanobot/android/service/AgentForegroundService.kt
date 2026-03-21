package com.nanobot.android.service

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.nanobot.android.NanoBotApplication
import com.nanobot.android.ui.MainActivity

private const val CHANNEL_ID = "nanobot_agent"
private const val NOTIFICATION_ID = 1001

/**
 * AgentForegroundService - Agent 前台服务
 *
 * 保持 AgentLoop 在后台运行，防止被系统杀死
 * 对应 NanoBot 的心跳服务
 */
class AgentForegroundService : Service() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        return START_STICKY  // 系统杀死后自动重启
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "NanoBot Agent",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "NanoBot AI 助手后台服务"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NanoBot")
            .setContentText("AI 助手正在运行")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
    }
}

/**
 * 开机自启动广播接收器
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val serviceIntent = Intent(context, AgentForegroundService::class.java)
            context.startForegroundService(serviceIntent)
        }
    }
}
