package com.morningwords.speech

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class SystemVoiceSelectionTest {
    private class Engine(val available: List<SystemVoiceOption> = emptyList(), val languages: Set<Locale> = emptySet(), val rejectVoices: Boolean = false) : SystemVoiceAccess {
        var selected: String? = null
        var language: Locale? = null
        override fun voices() = available
        override fun selectVoice(name: String): Boolean { if (rejectVoices) return false; selected = name; return true }
        override fun selectLanguage(locale: Locale): Boolean { if (locale !in languages) return false; language = locale; return true }
        override fun current() = SystemVoiceSelection(language, null)
    }
    @Test fun legacyEngineWithNoVoiceListCanSpeakEnglish() {
        val engine = Engine(languages = setOf(Locale.ENGLISH))
        val result = selectSystemVoice(engine, EnglishAccent.US)
        assertNotNull(result)
        assertEquals(Locale.ENGLISH, result!!.locale)
    }
    @Test fun missingCountryDoesNotRejectGenericEnglishVoice() {
        val engine = Engine(listOf(SystemVoiceOption("english", Locale.ENGLISH, false, 100)))
        assertNotNull(selectSystemVoice(engine, EnglishAccent.UK))
        assertEquals("english", engine.selected)
    }
    @Test fun requestedAccentAndOfflineVoiceTakePriority() {
        val engine = Engine(listOf(SystemVoiceOption("us", Locale.US, false, 500), SystemVoiceOption("uk-online", Locale.UK, true, 500), SystemVoiceOption("uk-offline", Locale.UK, false, 100)))
        assertEquals(Locale.UK, selectSystemVoice(engine, EnglishAccent.UK)!!.locale)
        assertEquals("uk-offline", engine.selected)
    }
    @Test fun alternateAccentIsReportedInsteadOfClaimingRequestedAccent() {
        val engine = Engine(listOf(SystemVoiceOption("us", Locale.US, false, 100)))
        assertEquals(Locale.US, selectSystemVoice(engine, EnglishAccent.UK)!!.locale)
    }
    @Test fun rejectedVoiceFallsBackToSetLanguage() {
        val engine = Engine(listOf(SystemVoiceOption("us", Locale.US, false, 100)), setOf(Locale.US), true)
        assertNotNull(selectSystemVoice(engine, EnglishAccent.US))
        assertEquals(Locale.US, engine.language)
    }
    @Test fun noEnglishSupportRequestsKokoroFallback() {
        assertNull(selectSystemVoice(Engine(listOf(SystemVoiceOption("zh", Locale.CHINA, false, 100))), EnglishAccent.US))
    }
}
