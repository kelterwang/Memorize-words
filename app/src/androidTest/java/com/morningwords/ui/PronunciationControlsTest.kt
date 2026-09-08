package com.morningwords.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.morningwords.MorningWordsApplication
import com.morningwords.domain.model.WordCard
import com.morningwords.speech.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PronunciationControlsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun manualAccentsPlayAndResetToPersistedDefaultsOnWordAndSettingChanges() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as MorningWordsApplication
        val repository = app.settingsRepository
        val previous = runBlocking { repository.settings.first() }
        var wordKey by mutableIntStateOf(1)
        lateinit var pronunciation: Pronunciation
        try {
            KokoroPack(app).ensureBundled()
            runBlocking {
                repository.setSpeechSource(SpeechSource.KOKORO)
                repository.setEnglishAccent(EnglishAccent.US)
            }
            compose.setContent {
                val settings by repository.settings.collectAsState(initial = previous)
                pronunciation = rememberPronunciation(settings.speechSource, settings.englishAccent, true, wordKey)
                MaterialTheme {
                    StudyWordCard(WordCard(wordKey.toLong(), "carsick", null, null, null, null, null), false, false,
                        pronunciation.player.ready, { pronunciation.speak("carsick", it) }, currentAccent = pronunciation.accent)
                }
            }
            compose.waitUntil(30_000) { pronunciation.player.ready && pronunciation.accent == EnglishAccent.US }
            compose.onNodeWithText("当前：美音").assertExists()
            compose.onNodeWithContentDescription("播放英音").performClick()
            compose.waitUntil(60_000) { pronunciation.accent == EnglishAccent.UK && pronunciation.player.completedPlaybackCount == 1 }
            compose.onNodeWithText("当前：英音").assertExists()
            assertEquals(EnglishAccent.US, runBlocking { repository.settings.first() }.englishAccent)
            compose.onNodeWithContentDescription("播放美音").performClick()
            compose.waitUntil(60_000) { pronunciation.accent == EnglishAccent.US && pronunciation.player.completedPlaybackCount == 1 }
            // The same repository writes used by My settings must update an already visible page.
            runBlocking { repository.setEnglishAccent(EnglishAccent.UK) }
            compose.waitUntil(30_000) { pronunciation.accent == EnglishAccent.UK && pronunciation.player.ready }
            compose.onNodeWithText("当前：英音").assertExists()
            compose.onNodeWithContentDescription("播放美音").performClick()
            compose.waitUntil(60_000) { pronunciation.accent == EnglishAccent.US && pronunciation.player.completedPlaybackCount == 1 }
            compose.runOnIdle { wordKey++ }
            compose.waitUntil(30_000) { pronunciation.accent == EnglishAccent.UK && pronunciation.player.ready }
            assertEquals(EnglishAccent.UK, runBlocking { repository.settings.first() }.englishAccent)
            runBlocking { repository.setSpeechSource(SpeechSource.SYSTEM) }
            compose.waitUntil(30_000) { pronunciation.player.ready && (pronunciation.player.status.contains("手机发音") || pronunciation.player.status.contains("已改用内置 Kokoro")) }
            compose.onNodeWithText("当前：英音").assertExists()
        } finally {
            runBlocking {
                repository.setSpeechSource(previous.speechSource)
                repository.setEnglishAccent(previous.englishAccent)
            }
        }
    }

    @Test fun bothButtonsStayAccessibleOnSmallLargeFontCardAndDisableWhilePreparing() {
        var enabled by mutableStateOf(false)
        val spoken = mutableListOf<EnglishAccent>()
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                MaterialTheme {
                    Box(Modifier.width(260.dp).height(280.dp)) {
                        StudyWordCard(WordCard(1, "homesick", null, null, null, null, null), false, true,
                            enabled, { spoken.add(it) }, currentAccent = EnglishAccent.UK)
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("播放美音").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithContentDescription("播放英音").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { enabled = true }
        compose.onNodeWithContentDescription("播放美音").performScrollTo().performClick()
        compose.onNodeWithContentDescription("播放英音").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(EnglishAccent.US, EnglishAccent.UK), spoken) }
    }
}
