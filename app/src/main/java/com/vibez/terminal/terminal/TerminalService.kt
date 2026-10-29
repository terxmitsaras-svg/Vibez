package com.vibez.terminal.terminal

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.vibez.terminal.MainActivity
import com.vibez.terminal.R
import com.vibez.terminal.VibezApp
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Foreground service that keeps the process, and with it all terminal sessions, alive while the app
 * is in the background. Stops itself when the last tab is closed.
 */
class TerminalService : Service() {

    private val scope = MainScope()
    private var wakeLock: PowerManager.WakeLock? = null
    private var tabCount = 0
    private var sshCount = 0

    private val sessions get() = (application as VibezApp).container.sessions

    override fun onCreate() {
        super.onCreate()
        createChannel()
        sessions.tabs.onEach { tabs ->
            if (tabs.isEmpty()) {
                stopSelf()
            } else {
                tabCount = tabs.size
                sshCount = tabs.count { it.kind == SessionKind.SSH }
                updateNotification()
            }
        }.launchIn(scope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Every startForegroundService() call must be answered with startForeground(), also when running.
        // The special use type only exists since Android 14; older versions reject unknown types.
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
        when (intent?.action) {
            ACTION_EXIT_ALL -> sessions.closeAll()
            ACTION_TOGGLE_WAKE_LOCK -> toggleWakeLock()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    // Held until the user releases it or the last session ends, like Termux; no timeout on purpose.
    @SuppressLint("WakelockTimeout")
    private fun toggleWakeLock() {
        if (wakeLock?.isHeld == true) {
            releaseWakeLock()
        } else {
            wakeLock = getSystemService<PowerManager>()
                ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Vibez:sessions")
                ?.apply { acquire() }
        }
        updateNotification()
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel_sessions), NotificationManager.IMPORTANCE_LOW)
        channel.description = getString(R.string.notification_channel_sessions_description)
        channel.setShowBadge(false)
        getSystemService<NotificationManager>()?.createNotificationChannel(channel)
    }

    private fun updateNotification() {
        getSystemService<NotificationManager>()?.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .setAction(MainActivity.ACTION_OPEN_TERMINAL)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val wakeLockHeld = wakeLock?.isHeld == true
        val text = buildString {
            append(resources.getQuantityString(R.plurals.session_count, tabCount, tabCount))
            if (sshCount > 0) append(" · ").append(resources.getQuantityString(R.plurals.ssh_count, sshCount, sshCount))
            if (wakeLockHeld) append(" · wake lock held")
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, "Exit all", serviceIntent(ACTION_EXIT_ALL))
            .addAction(0, if (wakeLockHeld) "Release wake lock" else "Acquire wake lock", serviceIntent(ACTION_TOGGLE_WAKE_LOCK))
            .build()
    }

    private fun serviceIntent(action: String): PendingIntent = PendingIntent.getService(
        this, action.hashCode(),
        Intent(this, TerminalService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        private const val CHANNEL_ID = "sessions"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_EXIT_ALL = "com.vibez.terminal.action.EXIT_ALL"
        private const val ACTION_TOGGLE_WAKE_LOCK = "com.vibez.terminal.action.TOGGLE_WAKE_LOCK"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, TerminalService::class.java))
        }
    }
}
