package com.nexora.player.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.nexora.player.MainActivity
import com.nexora.player.R
import com.nexora.player.data.update.RemoteAppNotification
import com.nexora.player.data.update.RemoteUpdateInfo

object RemoteUpdateNotifier {
    private const val CHANNEL_ID = "nexora_remote_updates"
    private const val CHANNEL_NAME = "Actualizaciones y avisos"
    private const val PREFS_NAME = "nexora_remote_notices"
    private const val KEY_SEEN_IDS = "seen_notice_ids"
    private const val KEY_LAST_UPDATE_NOTICE = "last_update_notice_version"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Avisos enviados desde el servidor de Nexora Player"
        }
        manager.createNotificationChannel(channel)
    }

    fun notifyUpdateAvailable(context: Context, update: RemoteUpdateInfo) {
        if (!update.available) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val version = update.latestVersion.versionCode
        if (prefs.getInt(KEY_LAST_UPDATE_NOTICE, 0) >= version) return

        ensureChannel(context)
        val text = if (update.required) {
            "Actualización obligatoria ${update.latestVersion.versionName} disponible."
        } else {
            "Nueva versión ${update.latestVersion.versionName} disponible para descargar."
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Actualización de Nexora Player")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(3300 + version, notification) }
        prefs.edit().putInt(KEY_LAST_UPDATE_NOTICE, version).apply()
    }

    fun notifyServerMessages(context: Context, notices: List<RemoteAppNotification>) {
        if (notices.isEmpty()) return
        ensureChannel(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val seen = prefs.getStringSet(KEY_SEEN_IDS, emptySet()).orEmpty().toMutableSet()
        var changed = false
        notices.filter { it.enabled }.forEachIndexed { index, notice ->
            if (notice.id in seen) return@forEachIndexed
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(notice.title.ifBlank { "Nexora Player" })
                .setContentText(notice.message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(notice.message))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(openAppIntent(context))
                .build()
            runCatching { NotificationManagerCompat.from(context).notify(3400 + index + seen.size, notification) }
            seen.add(notice.id)
            changed = true
        }
        if (changed) {
            prefs.edit().putStringSet(KEY_SEEN_IDS, seen).apply()
        }
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getActivity(context, 7701, intent, flags)
    }
}
