package com.morningwords.speech

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class KokoroOfflineTest {
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
