package com.morningwords.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioTrack
import android.media.AudioFormat
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

/** One screen owns playback. Native work is serialized and never runs on the UI thread. */
class SpeechPlayer(context: Context, private val source: SpeechSource, private val accent: EnglishAccent) {
    private val context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = nativeMutex
    private var native: OfflineTts? = null
    companion object { private val nativeMutex = Mutex() }
    private var system: TextToSpeech? = null
    private var request: Job? = null
    private var useKokoro = source == SpeechSource.KOKORO
    private var lastText: String? = null
    private var utterance: String? = null
    private var kokoroStatus = "Kokoro · ${accent.label} · 离线 · 1 倍速"
    private var active = true
    private var closed = false
    private var playbackGeneration = 0
    private var serial = 0
    var completedPlaybackCount by mutableStateOf(0); private set
    var isPlaying by mutableStateOf(false); private set
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
                if (selection == null) { prepareKokoro("手机未提供可确认的${accent.label}"); return@post }
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
        kokoroStatus = if (reason == null) "Kokoro · ${accent.label} · 离线 · 1 倍速" else "$reason，已改用内置 Kokoro ${accent.label} · 1 倍速"
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
            try {
                val audio = withContext(Dispatchers.IO) {
                    mutex.withLock {
                        ensureActive()
                        val engine = native ?: KokoroPack(context).create(accent).also { native = it }
                        engine.generate(text, sid = accent.speakerId, speed = SPEECH_RATE).also {
                            check(it.samples.isNotEmpty() && it.sampleRate > 0) { "Kokoro 无法生成该单词的发音" }
                        }
                    }
                }
                ensureActive()
                isPlaying = true
                status = "正在播放${accent.label}…"
                withContext(Dispatchers.IO) { playPcm(audio.samples, audio.sampleRate) }
                ensureActive()
                completedPlaybackCount++
                isPlaying = false
                status = kokoroStatus
            } catch (e: Exception) {
                if (e is CancellationException && e !is TimeoutCancellationException) throw e
                isPlaying = false
                fail("Kokoro 发音失败：${e.message ?: "请重试"}")
            }
        }
    }
    private suspend fun playPcm(samples: FloatArray, sampleRate: Int) {
        val pcm = toPcm16(samples)
        val minSize = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(minSize > 0) { "手机不支持该音频格式" }
        val bufferBytes = maxOf(minSize, sampleRate / 10 * 2)
        val track = AudioTrack.Builder().setAudioAttributes(attributes())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(bufferBytes).build()
        try {
            check(track.state == AudioTrack.STATE_INITIALIZED) { "无法打开手机音频输出" }
            track.play()
            var offset = 0
            while (offset < pcm.size) {
                currentCoroutineContext().ensureActive()
                val written = track.write(pcm, offset, minOf(bufferBytes / 2, pcm.size - offset), AudioTrack.WRITE_BLOCKING)
                check(written > 0) { "手机音频输出失败" }
                offset += written
            }
            // Wait for queued frames to reach the output before releasing the track.
            withTimeout(pcm.size.toLong() * 1000 / sampleRate + 5_000) {
                while (track.playbackHeadPosition.toLong() < pcm.size) delay(20)
            }
        } finally {
            if (track.state == AudioTrack.STATE_INITIALIZED) { track.pause(); track.flush() }
            track.release()
        }
    }

    fun setActive(value: Boolean) { active = value; if (!value) stop() }
    fun stop() {
        playbackGeneration++
        utterance = null; lastText = null
        request?.cancel(); request = null
        system?.stop()
        isPlaying = false
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
