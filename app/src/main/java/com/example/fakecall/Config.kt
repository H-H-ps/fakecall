package com.example.fakecall

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** ثوابت التطبيق — غيّر المدد من هنا. */
object CallConfig {
    const val RING_MS = 5_000L        // مدة الرنين
    const val ANSWERED_MS = 3_000L    // مدة المكالمة بعد الرد

    const val EXTRA_AUTO_ANSWER = "auto_answer"
    const val EXTRA_RING_START = "ring_start"
    const val EXTRA_DELAY_MS = "delay_ms"
}

/** حالة مشتركة بسيطة داخل نفس العملية. */
object CallState {
    /** true إذا فُتحت شاشة المكالمة أو تم الرفض من الإشعار. */
    @Volatile
    var consumed = false
}

data class Contact(
    val id: String,
    val name: String,
    /** اسم ملف الصورة داخل filesDir، أو null لاستخدام الصورة الافتراضية المدمجة. */
    val photoFile: String?
)

object Prefs {
    const val DEFAULT_ID = "default"
    const val DEFAULT_NAME = "العفوية"

    private fun sp(ctx: Context) =
        (ctx.applicationContext ?: ctx).getSharedPreferences("fakecall", Context.MODE_PRIVATE)

    fun newId(): String = UUID.randomUUID().toString()

    // ---------- جهات الاتصال ----------
    fun contacts(ctx: Context): List<Contact> {
        val raw = sp(ctx).getString("contacts", null)
        if (raw != null) {
            val list = runCatching {
                val arr = JSONArray(raw)
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    Contact(
                        o.getString("id"),
                        o.getString("name"),
                        if (o.isNull("photo")) null else o.getString("photo")
                    )
                }
            }.getOrNull()
            if (!list.isNullOrEmpty()) return list
        }
        return listOf(Contact(DEFAULT_ID, DEFAULT_NAME, null))
    }

    fun saveContacts(ctx: Context, list: List<Contact>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("name", it.name)
                    .put("photo", it.photoFile ?: JSONObject.NULL)
            )
        }
        sp(ctx).edit().putString("contacts", arr.toString()).apply()
    }

    fun activeId(ctx: Context): String = sp(ctx).getString("active_id", DEFAULT_ID) ?: DEFAULT_ID
    fun setActiveId(ctx: Context, id: String) = sp(ctx).edit().putString("active_id", id).apply()

    fun activeContact(ctx: Context): Contact {
        val all = contacts(ctx)
        return all.firstOrNull { it.id == activeId(ctx) } ?: all.first()
    }

    // ---------- التأخير قبل الرنين ----------
    fun delaySec(ctx: Context): Int = sp(ctx).getInt("delay_sec", 0)
    fun setDelaySec(ctx: Context, v: Int) = sp(ctx).edit().putInt("delay_sec", v).apply()

    // ---------- اللغة (ar / en) ----------
    fun lang(ctx: Context): String = sp(ctx).getString("lang", "ar") ?: "ar"
    fun setLang(ctx: Context, v: String) = sp(ctx).edit().putString("lang", v).apply()

    // ---------- النغمة والاهتزاز ----------
    fun ringtoneUri(ctx: Context): String? = sp(ctx).getString("ringtone", null)
    fun setRingtoneUri(ctx: Context, v: String?) = sp(ctx).edit().putString("ringtone", v).apply()

    fun vibrate(ctx: Context): Boolean = sp(ctx).getBoolean("vibrate", true)
    fun setVibrate(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("vibrate", v).apply()
}
