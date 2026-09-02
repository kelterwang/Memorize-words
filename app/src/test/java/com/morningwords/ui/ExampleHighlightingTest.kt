package com.morningwords.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleHighlightingTest {
    @Test fun `finds the word case insensitively without matching inside another word`() {
        val example = "I recommend it. This Recommendation is unrelated."

        val matches = findExampleWordRanges(example, "recommend")

        assertEquals(listOf("recommend"), matches.map { example.substring(it) })
    }

    @Test fun `finds common inflected forms of the imported word`() {
        val example = "The view attracts me, and it attracted many visitors."

        val matches = findExampleWordRanges(example, "attract")

        assertEquals(listOf("attracts", "attracted"), matches.map { example.substring(it) })
    }
}
