package com.morningwords.data.repository

import com.morningwords.data.entity.WordEntity
import com.morningwords.domain.importer.WordImporter
import org.junit.Assert.assertEquals
import org.junit.Test

class ImportedWordRepairTest {
    @Test fun `repairs legacy meaning containing part of speech and example`() {
        val parsed = WordImporter.parseText(
            "exchange n./vt. 交换；交流；兑换 I'm an exchange student from the UK."
        ).accepted.single()
        val legacy = WordEntity(
            id = 1,
            word = "exchange",
            normalizedWord = "exchange",
            meaning = "n./vt. 交换；交流；兑换 I'm an exchange student from the UK.",
            createdAt = 10,
            updatedAt = 10,
        )

        val repaired = repairWordFromRaw(legacy, parsed, now = 20)

        assertEquals("n./vt.", repaired.partOfSpeech)
        assertEquals("交换；交流；兑换", repaired.meaning)
        assertEquals("I'm an exchange student from the UK.", repaired.example)
        assertEquals(20, repaired.updatedAt)
    }

    @Test fun `reparses fields after importer rules improve`() {
        val parsed = WordImporter.parseText(
            "obviously adv. 显然；明显地 Obviously, we need to work harder."
        ).accepted.single()
        val previouslyMisparsed = WordEntity(
            id = 2,
            word = "obviously",
            normalizedWord = "obviously",
            partOfSpeech = "adv",
            meaning = "显然；明显地 Obviously,",
            example = "we need to work harder.",
            createdAt = 10,
            updatedAt = 10,
        )

        val repaired = repairWordFromRaw(previouslyMisparsed, parsed, now = 20)

        assertEquals("显然；明显地", repaired.meaning)
        assertEquals("Obviously, we need to work harder.", repaired.example)
        assertEquals(20, repaired.updatedAt)
    }
}
