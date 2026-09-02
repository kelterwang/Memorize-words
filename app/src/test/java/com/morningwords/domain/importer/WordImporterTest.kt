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
    }

    @Test fun `marks an invalid line without silently dropping it`() {
        val line = WordImporter.parseText("12345 ???").lines.single()
        assertEquals(ImportLineStatus.ERROR, line.status)
        assertNull(line.word)
    }
}

