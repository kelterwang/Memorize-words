package com.morningwords.speech

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class SystemVoiceSelectionTest {
    private class Engine(val available: List<SystemVoiceOption> = emptyList(), val languages: Set<Locale> = emptySet(), val rejectVoices: Boolean = false, val reported: Locale? = null) : SystemVoiceAccess {
        var selected: String? = null
        var language: Locale? = null
        override fun voices() = available
        override fun selectVoice(name: String): Boolean { if (rejectVoices) return false; selected = name; return true }
        override fun selectLanguage(locale: Locale): Boolean { if (locale !in languages) return false; language = locale; return true }
        override fun current() = SystemVoiceSelection(reported ?: available.firstOrNull { it.name == selected }?.locale ?: language, false)
    }
    @Test fun legacyEngineWithExactLanguageCanSpeakWithoutVoiceList() {
        val engine = Engine(languages = setOf(Locale.UK))
        assertEquals(Locale.UK, selectSystemVoice(engine, EnglishAccent.UK)!!.locale)
    }
    @Test fun genericEnglishDoesNotMasqueradeAsBothAccents() {
        val engine = Engine(listOf(SystemVoiceOption("english", Locale.ENGLISH, false, 100)), setOf(Locale.ENGLISH))
        assertNull(selectSystemVoice(engine, EnglishAccent.US))
        assertNull(selectSystemVoice(engine, EnglishAccent.UK))
    }
    @Test fun requestedAccentAndOfflineVoiceTakePriority() {
        val engine = Engine(listOf(SystemVoiceOption("us", Locale.US, false, 500), SystemVoiceOption("uk-online", Locale.UK, true, 500), SystemVoiceOption("uk-offline", Locale.UK, false, 100)))
        assertEquals(Locale.UK, selectSystemVoice(engine, EnglishAccent.UK)!!.locale)
        assertEquals("uk-offline", engine.selected)
    }
    @Test fun wrongAccentRequiresKokoroFallback() {
        val engine = Engine(listOf(SystemVoiceOption("us", Locale.US, false, 100)))
        assertNull(selectSystemVoice(engine, EnglishAccent.UK))
    }
    @Test fun engineIgnoringRequestedVoiceRequiresFallback() {
        val engine = Engine(listOf(SystemVoiceOption("uk", Locale.UK, false, 100)), setOf(Locale.UK), reported = Locale.US)
        assertNull(selectSystemVoice(engine, EnglishAccent.UK))
    }
    @Test fun rejectedVoiceFallsBackToExactLanguage() {
        val engine = Engine(listOf(SystemVoiceOption("us", Locale.US, false, 100)), setOf(Locale.US), true)
        assertNotNull(selectSystemVoice(engine, EnglishAccent.US))
    }
    @Test fun isoThreeCountryCodesAreRecognized() {
        assertTrue(matchesAccent(Locale("eng", "GBR"), EnglishAccent.UK))
        assertFalse(matchesAccent(Locale("eng", "USA"), EnglishAccent.UK))
    }
}
