package com.example.fakecall

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat

object CallNotifications {
    private const val ID_INCOMING = 1001
    private const val ID_MISSED = 1002
    private const val CH_INCOMING_PREFIX = "incoming_call_"
    private const val CH_MISSED_PREFIX = "missed_calls_"

    const val ACTION_DECLINE = "com.example.fakecall.DECLINE"

    private val piFlags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    /** القناة تُنشأ بمعرّف يتغيّر مع النغمة/الاهتزاز لأن إعدادات القناة لا تتغير بعد إنشائها. */
    private fun ensureIncomingChannel(ctx: Context, ringUri: Uri, vibrate: Boolean): String {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val lang = Prefs.lang(ctx)
        val id = CH_INCOMING_PREFIX + Integer.toHexString((ringUri.toString() + vibrate + lang).hashCode())
        if (nm.getNotificationChannel(id) == null) {
            nm.notificationChannels
                .filter { it.id.startsWith(CH_INCOMING_PREFIX) }
                .forEach { nm.deleteNotificationChannel(it.id) }

            val channel = NotificationChannel(
                id,
                LocaleHelper.wrap(ctx).getString(R.string.notif_channel_incoming),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setSound(
                    ringUri,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                enableVibration(vibrate)
                if (vibrate) vibrationPattern = longArrayOf(0, 800, 700, 800, 700)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
        return id
    }

    /**
     * إشعار مكالمة واردة بنمط CallStyle مع fullScreenIntent:
     * - الشاشة مقفلة/مطفأة: تفتح شاشة المكالمة تلقائياً.
     * - الجهاز مفتوح: يظهر إشعار منبثق (Heads-up) بزرّي رد ورفض، تماماً كالمكالمات الحقيقية.
     */
    @SuppressLint("MissingPermission")
    fun showIncoming(ctx: Context, contact: Contact) {
        val nm = NotificationManagerCompat.from(ctx)
        if (!nm.areNotificationsEnabled()) return

        val ringUri = Prefs.ringtoneUri(ctx)?.let(Uri::parse)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val channelId = ensureIncomingChannel(ctx, ringUri, Prefs.vibrate(ctx))

        val start = System.currentTimeMillis()
        val open = Intent(ctx, IncomingCallActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(CallConfig.EXTRA_RING_START, start)
        val openPi = PendingIntent.getActivity(ctx, 1, open, piFlags)

        val answer = Intent(open).putExtra(CallConfig.EXTRA_AUTO_ANSWER, true)
        val answerPi = PendingIntent.getActivity(ctx, 2, answer, piFlags)

        val decline = Intent(ctx, CallActionReceiver::class.java).setAction(ACTION_DECLINE)
        val declinePi = PendingIntent.getBroadcast(ctx, 3, decline, piFlags)

        val icon = ImageStore.squareIcon(ImageStore.loadBitmap(ctx, contact))
        val person = Person.Builder()
            .setName(contact.name)
            .setIcon(IconCompat.createWithBitmap(icon))
            .setImportant(true)
            .build()

        val notification = NotificationCompat.Builder(ctx, channelId)
            .setSmallIcon(R.drawable.ic_tile_call)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setTimeoutAfter(CallConfig.RING_MS)
            .setContentIntent(openPi)
            .setFullScreenIntent(openPi, true)
            .setStyle(NotificationCompat.CallStyle.forIncomingCall(person, declinePi, answerPi))
            .build()
            .apply { flags = flags or Notification.FLAG_INSISTENT }

        nm.notify(ID_INCOMING, notification)
    }

    fun cancelIncoming(ctx: Context) {
        NotificationManagerCompat.from(ctx).cancel(ID_INCOMING)
    }

    @SuppressLint("MissingPermission")
    fun showMissed(ctx: Context, contact: Contact) {
        val nm = NotificationManagerCompat.from(ctx)
        if (!nm.areNotificationsEnabled()) return

        val localized = LocaleHelper.wrap(ctx)
        val missedChannelId = CH_MISSED_PREFIX + Prefs.lang(ctx)
        val sys = ctx.getSystemService(NotificationManager::class.java)
        if (sys.getNotificationChannel(missedChannelId) == null) {
            sys.notificationChannels
                .filter { it.id.startsWith(CH_MISSED_PREFIX) }
                .forEach { sys.deleteNotificationChannel(it.id) }
            sys.createNotificationChannel(
                NotificationChannel(
                    missedChannelId,
                    localized.getString(R.string.notif_channel_missed),
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }

        val open = PendingIntent.getActivity(
            ctx, 10,
            Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            piFlags
        )
        val icon = ImageStore.squareIcon(ImageStore.loadBitmap(ctx, contact))

        val n = NotificationCompat.Builder(ctx, missedChannelId)
            .setSmallIcon(android.R.drawable.sym_call_missed)
            .setLargeIcon(icon)
            .setContentTitle(contact.name)
            .setContentText(localized.getString(R.string.missed_call))
            .setAutoCancel(true)
            .setContentIntent(open)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())
            .build()
        nm.notify(ID_MISSED, n)
    }
}
