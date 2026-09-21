package com.example.fakecall

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** زر «رفض» في الإشعار المنبثق. */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == CallNotifications.ACTION_DECLINE) {
            CallState.consumed = true
            CallNotifications.cancelIncoming(context)
            CallNotifications.showMissed(context, Prefs.activeContact(context))
        }
    }
}
