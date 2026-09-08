package com.morningwords.speech

import org.junit.Assert.*
import org.junit.Test

class PcmAudioTest {
    @Test fun preservesAudibleSamplesAndClampsWithoutOverflow() {
        assertArrayEquals(shortArrayOf(-32767, -16383, 0, 16383, 32767, 32767), toPcm16(floatArrayOf(-1f, -.5f, 0f, .5f, 1f, 2f)))
    }
    @Test fun invalidSamplesAreRejectedInsteadOfBecomingSilence() {
        for (invalid in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertTrue(runCatching { toPcm16(floatArrayOf(invalid)) }.isFailure)
            assertTrue(runCatching { validateSpeechAudio(floatArrayOf(.1f, invalid), 24000) }.isFailure)
        }
    }
    @Test fun emptySilentAndConstantSignalsAreRejected() {
        for (samples in listOf(floatArrayOf(), FloatArray(24000), FloatArray(24000) { 1f })) {
            assertTrue(runCatching { validateSpeechAudio(samples, 24000) }.isFailure)
        }
        assertTrue(runCatching { validateSpeechAudio(floatArrayOf(.1f, -.1f), 0) }.isFailure)
    }
    @Test fun varyingAudibleSignalIsAccepted() {
        validateSpeechAudio(floatArrayOf(0f, .1f, -.1f, .2f, -.2f), 24000)
    }
}
