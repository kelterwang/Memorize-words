package com.morningwords.speech

/** Reject invalid synthesis before it can become silent PCM or a constant output signal. */
internal fun validateSpeechAudio(samples: FloatArray, sampleRate: Int) {
    check(sampleRate > 0 && samples.isNotEmpty()) { "未生成发音，请重试" }
    var sum = 0.0
    var squares = 0.0
    for (sample in samples) {
        check(sample.isFinite()) { "语音合成结果异常，请重试" }
        sum += sample
        squares += sample.toDouble() * sample
    }
    val mean = sum / samples.size
    check(squares / samples.size - mean * mean > 0.000001) { "语音合成结果为静音，请重试" }
}

/** Signed PCM16 works across Android outputs without relying on WAV decoders. */
internal fun toPcm16(samples: FloatArray): ShortArray = ShortArray(samples.size) { index ->
    val value = samples[index]
    check(value.isFinite()) { "语音合成结果异常，请重试" }
    (value.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
}
