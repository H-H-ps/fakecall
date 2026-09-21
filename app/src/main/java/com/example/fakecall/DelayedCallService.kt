package com.example.fakecall

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager

/**
 * يحمل مؤقّت «التأخير قبل الرنين». يبقى WakeLock جزئياً حتى لا ينام المعالج والجوال في الجيب.
 * عند انتهاء المهلة ينشر إشعار المكالمة (fullScreenIntent)، وبعد مدة الرنين يتحقق إن لم يتفاعل
 * المستخدم فيسجّل «مكالمة فائتة».
 */
class DelayedCallService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handler.removeCallbacksAndMessages(null)
        releaseWakeLock()

        val delayMs = intent?.getLongExtra(CallConfig.EXTRA_DELAY_MS, 0L) ?: 0L
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "fakecall:delay").apply {
            acquire(delayMs + CallConfig.RING_MS + 5_000L)
        }

        handler.postDelayed({ fire() }, delayMs)
        return START_NOT_STICKY
    }

    private fun fire() {
        val contact = Prefs.activeContact(this)
        CallState.consumed = false
        CallNotifications.showIncoming(this, contact)

        handler.postDelayed({
            if (!CallState.consumed) {
                CallNotifications.cancelIncoming(this)
                CallNotifications.showMissed(this, contact)
            }
            stopSelf()
        }, CallConfig.RING_MS + 300L)
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        releaseWakeLock()
        super.onDestroy()
    }
}
