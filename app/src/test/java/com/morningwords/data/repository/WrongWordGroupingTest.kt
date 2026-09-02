package com.morningwords.data.repository

import com.morningwords.data.entity.WrongWordBatchLink
import com.morningwords.data.entity.WrongWordEntity
import com.morningwords.data.entity.WrongWordRow
import com.morningwords.data.entity.WordEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class WrongWordGroupingTest {
    @Test fun `groups active wrong words by imported library and preserves import order`() {
        val alpha = wrongWord(1, "alpha")
        val beta = wrongWord(2, "beta")
        val links = listOf(
            link(batchId = 20, batchName = "第二单元", wordId = 2, sortOrder = 0),
            link(batchId = 10, batchName = "第一单元", wordId = 1, sortOrder = 0),
            link(batchId = 10, batchName = "第一单元", wordId = 2, sortOrder = 1),
        )

        val groups = groupWrongWordsByBatch(links, listOf(beta, alpha))

        assertEquals(listOf("第二单元", "第一单元"), groups.map { it.batchName })
        assertEquals(listOf("alpha", "beta"), groups[1].words.map { it.word.word })
        assertEquals(listOf(1, 2), groups.map { it.words.size })
    }

    private fun link(batchId: Long, batchName: String, wordId: Long, sortOrder: Int) =
        WrongWordBatchLink(batchId, batchName, batchId, wordId, sortOrder)

    private fun wrongWord(id: Long, value: String) = WrongWordRow(
        WrongWordEntity(id = id, wordId = id, firstWrongAt = 1, lastWrongAt = 1, wrongCount = 1),
        WordEntity(id = id, word = value, normalizedWord = value, createdAt = 1, updatedAt = 1),
    )
}
