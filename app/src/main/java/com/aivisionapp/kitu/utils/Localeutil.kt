package com.aivisionapp.kitu.utils

import android.content.Context
import java.util.Locale

fun getCurrentAppLanguage(context: Context): String {
    val configuration = context.resources.configuration
    val locale = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
        configuration.locales.get(0)
    } else {
        @Suppress("DEPRECATION")
        configuration.locale
    }

    return locale?.language ?: Locale.getDefault().language
}