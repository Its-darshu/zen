package com.zenmode.app.system

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.zenmode.app.MainActivity
import com.zenmode.app.R
import com.zenmode.app.core.time.DurationFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The app's two notifications (specification §13 of the phase brief, §26).
 *
 * - an ongoing, silent one while a session runs, because a foreground service
 *   must be visible and the user should always be able to see that Zen Mode is
 *   on and get back to it;
 * - one when a session genuinely completes, if the user asked for it.
 *
 * Nothing else is ever posted.
 */
@Singleton
class ZenNotifications @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ZenNotifier {

    private val notificationManager: NotificationManager?
        get() = context.getSystemService(NotificationManager::class.java)

    fun ensureChannels() {
        val manager = notificationManager ?: return

        val active = NotificationChannel(
            CHANNEL_ACTIVE,
            context.getString(R.string.channel_active_session),
            // Low: it must be visible, but a focus session should never buzz.
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.channel_active_session_description)
            setShowBadge(false)
        }

        val completion = NotificationChannel(
            CHANNEL_COMPLETION,
            context.getString(R.string.channel_completion),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.channel_completion_description)
        }

        manager.createNotificationChannel(active)
        manager.createNotificationChannel(completion)
    }

    /**
     * The ongoing-session notification.
     *
     * The countdown is drawn by the system from [scheduledEndAt] via the
     * chronometer, so the service does not have to repost a notification every
     * second to keep it honest — and the displayed time comes from the same
     * timestamp as everything else.
     */
    fun buildActiveNotification(scheduledEndAt: Long, remainingSeconds: Long): Notification =
        NotificationCompat.Builder(context, CHANNEL_ACTIVE)
            .setSmallIcon(R.drawable.ic_zen_notification)
            .setContentTitle(context.getString(R.string.notification_active_title))
            .setContentText(
                context.getString(
                    R.string.notification_active_text,
                    DurationFormat.sessionLength(remainingSeconds),
                ),
            )
            .setWhen(scheduledEndAt)
            .setUsesChronometer(true)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    setChronometerCountDown(true)
                }
            }
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openAppIntent())
            .build()

    override fun notifySessionCompleted(focusedSeconds: Long) {
        if (!canPostNotifications()) return
        ensureChannels()

        val notification = NotificationCompat.Builder(context, CHANNEL_COMPLETION)
            .setSmallIcon(R.drawable.ic_zen_notification)
            .setContentTitle(context.getString(R.string.notification_complete_title))
            .setContentText(
                context.getString(
                    R.string.notification_complete_text,
                    DurationFormat.sessionLength(focusedSeconds),
                ),
            )
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(openAppIntent())
            .build()

        runCatching { NotificationManagerCompat.from(context).notify(ID_COMPLETION, notification) }
    }

    override fun cancelCompletionNotification() {
        runCatching { NotificationManagerCompat.from(context).cancel(ID_COMPLETION) }
    }

    /**
     * On Android 13+ notifications need runtime permission. Without it the
     * session still runs — the notification simply is not shown, and nothing
     * here pretends otherwise.
     */
    override fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun openAppIntent(): PendingIntent? {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return runCatching {
            PendingIntent.getActivity(
                context,
                REQUEST_OPEN_APP,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }.getOrNull()
    }

    companion object {
        const val CHANNEL_ACTIVE = "zen_active_session"
        const val CHANNEL_COMPLETION = "zen_session_complete"
        const val ID_ACTIVE = 2001
        const val ID_COMPLETION = 2002
        private const val REQUEST_OPEN_APP = 1002
    }
}
