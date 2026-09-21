package com.example.fakecall

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * نقطة الدخول الموحّدة (من الـ Tile ومن زر «جرّب الآن»).
 * شفافة وتنتهي فوراً: إمّا تفتح المكالمة مباشرة، أو تشغّل خدمة التأخير.
 */
class TriggerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val delaySec = Prefs.delaySec(this)
        if (delaySec <= 0) {
            CallState.consumed = true
            startActivity(
                Intent(this, IncomingCallActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        } else {
            startService(
                Intent(this, DelayedCallService::class.java)
                    .putExtra(CallConfig.EXTRA_DELAY_MS, delaySec * 1000L)
            )
        }
        finish()
    }
}
