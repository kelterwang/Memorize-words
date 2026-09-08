package com.morningwords.speech

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class AccentPlaybackTest {
    @Test fun britishFirstAndRepeatedAccentSwitchesReachAudioOutput() = runBlocking {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val context = instrument.targetContext
        KokoroPack(context).ensureBundled()
        for (accent in listOf(EnglishAccent.UK, EnglishAccent.US, EnglishAccent.UK)) {
            lateinit var player: SpeechPlayer
            instrument.runOnMainSync { player = SpeechPlayer(context, SpeechSource.KOKORO, accent) }
            try {
                withTimeout(30_000) {
                    while (true) {
                        var ready = false
                        instrument.runOnMainSync { ready = player.ready }
                        if (ready) break
                        delay(50)
                    }
                }
                for ((index, word) in listOf("carsick", "homesick", "unique").withIndex()) {
                    instrument.runOnMainSync { player.speak(word) }
                    withTimeout(60_000) {
                        while (true) {
                            var done = false
                            instrument.runOnMainSync {
                                assertNull(player.error)
                                done = player.completedPlaybackCount == index + 1
                            }
                            if (done) break
                            delay(50)
                        }
                    }
                    instrument.runOnMainSync {
                        assertFalse(player.isPlaying)
                        assertTrue(player.status.contains(accent.label))
                    }
                }
            } finally { instrument.runOnMainSync { player.close() } }
        }
    }
}
