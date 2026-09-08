package com.morningwords.speech

/** Signed PCM16 works across Android outputs without relying on WAV decoders. */
internal fun toPcm16(samples: FloatArray): ShortArray = ShortArray(samples.size) { index ->
    val value = samples[index]
    if (!value.isFinite()) 0 else (value.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
}
