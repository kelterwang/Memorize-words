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

/** Some Android engines expose no Voice list, but do support setLanguage(). */
internal fun selectSystemVoice(access: SystemVoiceAccess, accent: EnglishAccent): SystemVoiceSelection? {
    val candidates = access.voices().filter { it.locale.language in setOf("en", "eng") }
        .sortedWith(compareByDescending<SystemVoiceOption> { it.locale.country == accent.locale.country }
            .thenBy { it.network }.thenByDescending { it.quality })
    for (voice in candidates) {
        if (access.selectVoice(voice.name)) return SystemVoiceSelection(voice.locale, voice.network)
    }
    for (locale in listOf(accent.locale, Locale.ENGLISH, Locale.US, Locale.UK).distinct()) {
        if (access.selectLanguage(locale)) return access.current()
    }
    return null
}
