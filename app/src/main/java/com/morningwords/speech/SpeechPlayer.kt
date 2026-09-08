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
    private var closed = false
    private var serial = 0
    private var audioFile: File? = null
    var ready by mutableStateOf(false); private set
    var status by mutableStateOf("正在准备发音…"); private set
    var error by mutableStateOf<String?>(null); private set

    init {
        if (source == SpeechSource.KOKORO) {
            ready = KokoroPack(this.context).installed
            status = if (ready) "Kokoro · 离线 · 1 倍速" else "请先导入 Kokoro 离线语音包"
        } else {
            system = TextToSpeech(this.context) { result -> main.post {
                if (closed) return@post
                val tts = system ?: return@post
                if (result != TextToSpeech.SUCCESS) { fail("手机语音初始化失败，请检查系统文字转语音设置"); return@post }
                val voice = tts.voices.orEmpty().filter {
                    it.locale.language == "en" && it.locale.country == accent.locale.country
                }.sortedWith(compareBy<android.speech.tts.Voice> { it.isNetworkConnectionRequired }.thenByDescending { it.quality }).firstOrNull()
                if (voice == null || tts.setVoice(voice) == TextToSpeech.ERROR) {
                    fail("手机缺少${if (accent == EnglishAccent.US) "美音" else "英音"}语音，请安装对应语音数据或切换口音")
                    return@post
                }
                tts.setSpeechRate(SPEECH_RATE)
                tts.setPitch(1f)
                tts.setAudioAttributes(attributes())
                ready = true
                status = if (voice.isNetworkConnectionRequired) "手机发音 · 此声音需要联网 · 1 倍速" else "手机发音 · 离线声音 · 1 倍速"
            } }
            system?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) = Unit
                @Deprecated("Deprecated by Android")
                override fun onError(utteranceId: String?) { main.post { if (!closed) fail("手机发音失败，请检查语音数据和媒体音量") } }
            })
        }
    }
    private fun attributes() = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private fun fail(message: String) { error = message; status = message }
    fun clearError() { error = null }
    fun speak(text: String) {
        stop()
        error = null
        if (!ready) { fail(status); return }
        if (source == SpeechSource.SYSTEM) {
            if (system?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "word-${++serial}") == TextToSpeech.ERROR) fail("手机发音失败，请重试")
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
                    setOnCompletionListener { stop(); status = "Kokoro · 离线 · 1 倍速" }
                    setOnErrorListener { _, _, _ -> stop(); fail("离线音频播放失败，请重试"); true }
                    prepare()
                    start()
                }
                status = "Kokoro · 离线 · 1 倍速"
            } catch (e: Exception) {
                output.delete()
                if (e is CancellationException) throw e
                fail("Kokoro 发音失败：${e.message ?: "请重新选择语音包"}")
            }
        }
    }
    fun stop() {
        request?.cancel(); request = null
        system?.stop()
        media?.release(); media = null
        audioFile?.delete(); audioFile = null
        if (source == SpeechSource.KOKORO && ready) status = "Kokoro · 离线 · 1 倍速"
    }
    fun close() {
        closed = true
        stop()
        system?.shutdown(); system = null
        // Wait for any in-flight native generation before releasing its pointer.
        scope.launch(Dispatchers.IO) { mutex.withLock { native?.release(); native = null }; scope.cancel() }
    }
}
