package com.morningwords.speech

import org.junit.Assert.*
import org.junit.Test

class PcmAudioTest {
    @Test fun preservesAudibleSamplesAndClampsWithoutOverflow() {
        assertArrayEquals(shortArrayOf(-32767, -16383, 0, 16383, 32767, 32767), toPcm16(floatArrayOf(-1f, -.5f, 0f, .5f, 1f, 2f)))
    }
    @Test fun invalidSamplesBecomeSilence() {
        assertArrayEquals(shortArrayOf(0, 0, 0), toPcm16(floatArrayOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)))
    }
}
