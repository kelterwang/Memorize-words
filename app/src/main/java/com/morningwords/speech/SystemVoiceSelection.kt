package com.morningwords.speech

import java.util.Locale

internal data class SystemVoiceOption(val name: String, val locale: Locale, val network: Boolean, val quality: Int)
internal data class SystemVoiceSelection(val locale: Locale?, val network: Boolean?)
internal interface SystemVoiceAccess {
    fun voices(): List<SystemVoiceOption>
    fun selectVoice(name: String): Boolean
    fun selectLanguage(locale: Locale): Boolean
    fun current(): SystemVoiceSelection
}

internal fun matchesAccent(locale: Locale?, accent: EnglishAccent): Boolean =
    locale?.language in setOf("en", "eng") && when (accent) {
        EnglishAccent.US -> locale?.country in setOf("US", "USA")
        EnglishAccent.UK -> locale?.country in setOf("GB", "GBR")
    }

/** A successful API call is insufficient: verify the engine's actual selected accent. */
internal fun selectSystemVoice(access: SystemVoiceAccess, accent: EnglishAccent): SystemVoiceSelection? {
    val candidates = access.voices().filter { matchesAccent(it.locale, accent) }
        .sortedWith(compareBy<SystemVoiceOption> { it.network }.thenByDescending { it.quality })
    for (voice in candidates) {
        if (access.selectVoice(voice.name)) {
            val actual = access.current()
            if (matchesAccent(actual.locale, accent)) return actual
        }
    }
    if (access.selectLanguage(accent.locale)) {
        val actual = access.current()
        if (matchesAccent(actual.locale, accent)) return actual
    }
    return null
}
