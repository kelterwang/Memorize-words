package com.morningwords.speech

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class KokoroOfflineTest {
    @Test fun reportedAndCommonSingleWordsProduceAudibleSpeechInBothAccents() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val pack = KokoroPack(context)
        pack.ensureBundled()
        for (accent in listOf(EnglishAccent.UK, EnglishAccent.US)) {
            val engine = pack.create(accent)
            try {
                for (word in listOf("carsick", "homesick", "unique", "apple", "cat", "book", "school", "water", "family", "beautiful", "tomato", "schedule", "comfortable", "a", "I")) {
                    val audio = engine.generate(word, accent.speakerId, SPEECH_RATE)
                    validateSpeechAudio(audio.samples, audio.sampleRate)
                    val rms = kotlin.math.sqrt(audio.samples.map { it.toDouble() * it }.average())
                    android.util.Log.i("WordAudioTest", "$accent $word samples=${audio.samples.size} rms=$rms")
                    if (word in listOf("carsick", "homesick", "unique")) {
                        assertTrue(audio.save(File(context.getExternalFilesDir(null), "${accent.name}-$word.wav").absolutePath))
                    }
                    assertTrue("$word has no audible speech: rms=$rms", rms > .005)
                }
            } finally { engine.release() }
        }
    }

    @Test fun realPackGeneratesBothAccentsWithoutNetworkPermission() {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val context = instrument.targetContext
        val pack = KokoroPack(context)
        // Use only APK assets: no external ZIP, download or test fixture is installed.
        pack.ensureBundled()
        assertTrue(pack.installed)
        val outputs = EnglishAccent.entries.map { accent ->
            val engine = pack.create(accent)
            try {
                assertTrue(engine.numSpeakers() > accent.speakerId)
                val result = engine.generate("Tomato. Schedule. A fresh start every morning.", accent.speakerId, SPEECH_RATE)
                assertEquals(24000, result.sampleRate)
                validateSpeechAudio(result.samples, result.sampleRate)
                assertTrue(result.samples.size > 24000)
                assertTrue(result.samples.any { kotlin.math.abs(it) > .01f })
                assertTrue(result.save(File(context.getExternalFilesDir(null), "kokoro-${accent.name}.wav").absolutePath))
                result.samples
            } finally { engine.release() }
        }
        assertFalse(outputs[0].contentEquals(outputs[1]))
        val permissions = context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        assertFalse(permissions.contains("android.permission.INTERNET"))
    }
}
