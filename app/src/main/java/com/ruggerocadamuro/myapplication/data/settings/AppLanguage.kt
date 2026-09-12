package com.ruggerocadamuro.myapplication.data.settings

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * Lingue supportate dall'app. La scelta viene fatta nel setup iniziale
 * (obbligatorio) e resta modificabile dalle impostazioni.
 *
 * [nativeName] e' volutamente scritto nella lingua stessa: quando si sceglie
 * una lingua la si vuole riconoscere a colpo d'occhio, non tradotta.
 */
enum class AppLanguage(val tag: String, val nativeName: String) {
    IT("it", "Italiano"),
    EN("en", "English"),
    DE("de", "Deutsch"),
    ES("es", "Español");

    companion object {
        val DEFAULT = IT

        fun fromTag(tag: String?): AppLanguage? =
            tag?.let { value -> entries.firstOrNull { it.tag.equals(value, ignoreCase = true) } }

        /** Lingua di sistema se supportata, altrimenti italiano. */
        fun systemDefault(): AppLanguage = fromTag(Locale.getDefault().language) ?: DEFAULT
    }
}

/**
 * Applica la lingua scelta a tutta l'app.
 *
 * Due meccanismi, perche' nessuno dei due copre da solo tutte le versioni:
 *  - Android 13+ (API 33): `LocaleManager` imposta la lingua per-app gestita
 *    dal sistema (la stessa che compare in Impostazioni > App > Lingua) e fa
 *    ricreare le Activity da solo.
 *  - Tutte le versioni: [wrap] avvolge il Context di Activity/Service con la
 *    Configuration della lingua scelta, cosi' `getString` e Compose leggono
 *    subito le risorse giuste.
 *
 * La lingua viene copiata anche su SharedPreferences: `attachBaseContext`
 * o `onCreate` devono poterla leggere senza attendere DataStore (che e'
 * asincrono e bloccherebbe l'avvio).
 */
object AppLocale {

    private const val PREFS = "vesc_locale"
    private const val KEY = "language_tag"

    /** Lingua attualmente in uso: prima il sistema, poi la copia locale. */
    fun current(context: Context): AppLanguage {
        systemTag(context)?.let { tag ->
            fromTagOrNull(tag)?.let { language ->
                store(context, language)
                return language
            }
        }
        return fromTagOrNull(prefs(context).getString(KEY, null)) ?: AppLanguage.systemDefault()
    }

    /** Copia sincrona della scelta (letta da attachBaseContext). */
    fun store(context: Context, language: AppLanguage) {
        prefs(context).edit().putString(KEY, language.tag).apply()
    }

    /** Applica la lingua: sistema (API 33+) + copia locale. */
    fun apply(context: Context, language: AppLanguage) {
        store(context, language)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                context.getSystemService(LocaleManager::class.java)
                    ?.applicationLocales = LocaleList.forLanguageTags(language.tag)
            }
        }
    }

    /** Context avvolto con la lingua scelta: usato da Activity e Service. */
    fun wrap(base: Context): Context {
        val locale = Locale.forLanguageTag(current(base).tag)
        Locale.setDefault(locale)
        val configuration = Configuration(base.resources.configuration).apply {
            setLocale(locale)
            setLocales(LocaleList(locale))
        }
        return base.createConfigurationContext(configuration)
    }

    private fun systemTag(context: Context): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        return runCatching {
            val locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales
            if (locales == null || locales.isEmpty) null else locales.toLanguageTags().substringBefore(',')
        }.getOrNull()
    }

    private fun fromTagOrNull(tag: String?): AppLanguage? = AppLanguage.fromTag(tag)

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
