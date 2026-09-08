package com.morningwords.speech

import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.morningwords.MorningWordsApplication
import com.morningwords.ui.AppViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class BundledVoiceStartupTest {
    @Test fun oldInt8MarkerDoesNotSkipInstallingFp32Pack() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val root = java.io.File(base.cacheDir, "voice-upgrade-test").apply { deleteRecursively(); mkdirs() }
        val context = object : android.content.ContextWrapper(base) {
            override fun getNoBackupFilesDir() = root
        }
        try {
            val legacy = java.io.File(root, "kokoro-v1").apply { mkdirs() }
            java.io.File(legacy, "installed").writeText("1")
            val pack = KokoroPack(context)
            assertFalse(pack.installed)
            pack.ensureBundled()
            assertTrue(pack.installed)
            assertTrue(java.io.File(pack.directory, "model.onnx").length() > 300_000_000)
            assertFalse(legacy.exists())
        } finally { root.deleteRecursively() }
    }

    @Test fun applicationPreparesPackWithoutExternalFileAndSystemFallbackBecomesReady() = runBlocking {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val app = instrument.targetContext.applicationContext as MorningWordsApplication
        val store = ViewModelStore()
        lateinit var vm: AppViewModel
        instrument.runOnMainSync { vm = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(app))[AppViewModel::class.java] }
        try {
            withTimeout(60_000) { vm.state.first { it.voicePackInstalled && !it.voicePackImporting } }
            assertTrue(KokoroPack(app).installed)
            lateinit var player: SpeechPlayer
            instrument.runOnMainSync { player = SpeechPlayer(app, SpeechSource.SYSTEM, EnglishAccent.US) }
            try {
                withTimeout(30_000) {
                    while (true) {
                        var ready = false
                        instrument.runOnMainSync { ready = player.ready }
                        if (ready) break
                        delay(100)
                    }
                }
                instrument.runOnMainSync {
                    player.setActive(false)
                    player.speak("Apple")
                    assertFalse(player.status.contains("正在合成"))
                    assertNull(player.error)
                    assertTrue(player.status.contains("手机发音") || player.status.contains("已改用内置 Kokoro"))
                }
            } finally { instrument.runOnMainSync { player.close() } }
        } finally { instrument.runOnMainSync { store.clear() } }
    }
}
