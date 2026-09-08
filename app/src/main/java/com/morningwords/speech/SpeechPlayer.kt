package com.morningwords.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.k2fsa.sherpa.onnx.OfflineTts
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/** One screen owns playback. Native work is serialized and never runs on the UI thread. */
class SpeechPlayer(context: Context, private val source: SpeechSource, private val accent: EnglishAccent) {
    private val context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = nativeMutex
    private var native: OfflineTts? = null
    companion object { private val nativeMutex = Mutex() }
    private var system: TextToSpeech? = null
    private var media: MediaPlayer? = null
    private var request: Job? = null
    private var useKokoro = source == SpeechSource.KOKORO
    private var lastText: String? = null
    private var utterance: String? = null
    private var kokoroStatus = "Kokoro · 离线 · 1 倍速"
    private var active = true
    private var closed = false
    private var playbackGeneration = 0
    private var serial = 0
    private var audioFile: File? = null
    var ready by mutableStateOf(false); private set
    var status by mutableStateOf("正在准备发音…"); private set
    var error by mutableStateOf<String?>(null); private set

    init {
        if (useKokoro) {
            prepareKokoro()
        } else {
            system = TextToSpeech(this.context) { result -> main.post {
                if (closed) return@post
                val tts = system ?: return@post
                if (result != TextToSpeech.SUCCESS) { prepareKokoro("手机语音未能启动"); return@post }
                val selection = selectSystemVoice(object : SystemVoiceAccess {
                    override fun voices() = tts.voices.orEmpty().map { SystemVoiceOption(it.name, it.locale, it.isNetworkConnectionRequired, it.quality) }
                    override fun selectVoice(name: String) = tts.voices.orEmpty().firstOrNull { it.name == name }?.let { tts.setVoice(it) != TextToSpeech.ERROR } ?: false
                    override fun selectLanguage(locale: java.util.Locale) = tts.setLanguage(locale) >= TextToSpeech.LANG_AVAILABLE
                    @Suppress("DEPRECATION")
                    override fun current() = SystemVoiceSelection(tts.voice?.locale ?: tts.language, tts.voice?.isNetworkConnectionRequired)
                }, accent)
                if (selection == null) { prepareKokoro("手机没有可用的英文语音"); return@post }
                tts.setSpeechRate(SPEECH_RATE)
                tts.setPitch(1f)
                tts.setAudioAttributes(attributes())
                ready = true
                val actualAccent = when (selection.locale?.country) {
                    "US", "USA" -> "美音"
                    "GB", "GBR" -> "英音"
                    else -> "系统英文口音"
                }
                val connection = when (selection.network) { true -> "需联网"; false -> "离线"; null -> "联网需求由系统决定" }
                status = "手机发音 · $actualAccent · $connection · 1 倍速"
            } }
            system?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) = Unit
                @Deprecated("Deprecated by Android")
                override fun onError(utteranceId: String?) { main.post {
                    if (!closed && utteranceId == utterance && lastText != null) prepareKokoro("手机语音播放失败", lastText)
                } }
            })
        }
    }
    private fun prepareKokoro(reason: String? = null, replay: String? = null) {
        useKokoro = true
        ready = false
        system?.stop()
        utterance = null
        kokoroStatus = if (reason == null) "Kokoro · 离线 · 1 倍速" else "$reason，已改用内置 Kokoro · 1 倍速"
        status = "正在准备内置离线语音…"
        val generation = playbackGeneration
        scope.launch {
            try {
                withContext(Dispatchers.IO) { KokoroPack(context).ensureBundled() }
                if (closed) return@launch
                ready = true
                status = kokoroStatus
                if (replay != null && generation == playbackGeneration) speak(replay)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                fail("内置语音准备失败，请在“我的”重试：${e.message}")
            }
        }
    }
    private fun attributes() = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private fun fail(message: String) { error = message; status = message }
    fun clearError() { error = null }
    fun speak(text: String) {
        if (closed || !active) return
        stop()
        error = null
        if (!ready) { fail(status); return }
        lastText = text
        if (!useKokoro) {
            utterance = "word-${++serial}"
            if (system?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utterance) == TextToSpeech.ERROR) prepareKokoro("手机语音播放失败", text)
            return
        }
        status = "正在合成离线发音…"
        request = scope.launch {
            val output = File.createTempFile("kokoro-", ".wav", context.cacheDir)
            try {
                withContext(Dispatchers.IO) {
                    mutex.withLock {
                        ensureActive()
                        val engine = native ?: KokoroPack(context).create(accent).also { native = it }
                        val audio = engine.generate(text, sid = accent.speakerId, speed = SPEECH_RATE)
                        check(audio.samples.isNotEmpty() && audio.save(output.absolutePath)) { "Kokoro 无法生成该单词的发音" }
                    }
                }
                ensureActive()
                audioFile = output
                media = MediaPlayer().apply {
                    setAudioAttributes(attributes())
                    setDataSource(output.absolutePath)
                    setOnCompletionListener { stop(); status = kokoroStatus }
                    setOnErrorListener { _, _, _ -> stop(); fail("离线音频播放失败，请重试"); true }
                    prepare()
                    start()
                }
                status = kokoroStatus
            } catch (e: Exception) {
                output.delete()
                if (e is CancellationException) throw e
                fail("Kokoro 发音失败：${e.message ?: "请重新选择语音包"}")
            }
        }
    }
    fun setActive(value: Boolean) { active = value; if (!value) stop() }
    fun stop() {
        playbackGeneration++
        utterance = null; lastText = null
        request?.cancel(); request = null
        system?.stop()
        media?.release(); media = null
        audioFile?.delete(); audioFile = null
        if (useKokoro && ready) status = kokoroStatus
    }
    fun close() {
        closed = true
        stop()
        system?.shutdown(); system = null
        // Wait for any in-flight native generation before releasing its pointer.
        scope.launch(Dispatchers.IO) { mutex.withLock { native?.release(); native = null }; scope.cancel() }
    }
}
