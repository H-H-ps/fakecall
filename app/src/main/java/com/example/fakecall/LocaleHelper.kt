package com.example.fakecall

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** يطبّق لغة التطبيق المختارة (ar / en) على أي Context. */
object LocaleHelper {

    fun wrap(base: Context): Context {
        val locale = Locale.forLanguageTag(Prefs.lang(base))
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }
}
