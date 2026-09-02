package com.morningwords.domain.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WordImporterTest {
    @Test fun `normalizes and removes duplicates within a batch`() {
        val preview = WordImporter.parseText("Maintain v. 保持\n maintain \nachieve")
        assertEquals(2, preview.accepted.size)
        assertEquals(1, preview.duplicateCount)
        assertEquals("maintain", preview.accepted.first().normalizedWord)
    }

    @Test fun `extracts part of speech and meaning`() {
        val line = WordImporter.parseText("achieve v. 实现；达到").accepted.single()
        assertEquals("v", line.partOfSpeech)
        assertEquals("实现；达到", line.meaning)
        assertNull(line.example)
    }

    @Test fun `separates combined part of speech meaning and example`() {
        val line = WordImporter.parseText(
            "exchange n./vt.  交换；交流；兑换  I'm an exchange student from the UK."
        ).accepted.single()

        assertEquals("n./vt.", line.partOfSpeech)
        assertEquals("交换；交流；兑换", line.meaning)
        assertEquals("交换；交流；兑换", line.requiredMeaning)
        assertEquals("I'm an exchange student from the UK.", line.example)
    }

    @Test fun `does not mistake an English definition for an example`() {
        val line = WordImporter.parseText("maintain v. keep something in good condition").accepted.single()

        assertEquals("keep something in good condition", line.meaning)
        assertNull(line.example)
    }

    @Test fun `keeps a leading adverb with its example sentence`() {
        val line = WordImporter.parseText(
            "obviously adv. 显然；明显地 Obviously, we need to work harder."
        ).accepted.single()

        assertEquals("显然；明显地", line.meaning)
        assertEquals("Obviously, we need to work harder.", line.example)
    }

    @Test fun `marks an invalid line without silently dropping it`() {
        val line = WordImporter.parseText("12345 ???").lines.single()
        assertEquals(ImportLineStatus.ERROR, line.status)
        assertNull(line.word)
    }
}
