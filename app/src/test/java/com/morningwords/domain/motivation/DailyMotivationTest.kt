package com.morningwords.domain.motivation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyMotivationTest {
    @Test fun `greeting follows the system hour`() {
        assertEquals("夜深了", greetingForHour(2))
        assertEquals("早上好", greetingForHour(7))
        assertEquals("中午好", greetingForHour(12))
        assertEquals("下午好", greetingForHour(15))
        assertEquals("晚上好", greetingForHour(21))
    }

    @Test fun `all supplied quotes are present`() {
        assertEquals(31, DAILY_QUOTES.size)
        assertEquals(31, DAILY_QUOTES.distinct().size)
        assertEquals("为人民服务", DAILY_QUOTES.first())
        assertEquals("踏遍青山人未老", DAILY_QUOTES.last())
    }

    @Test fun `same date keeps the persisted quote`() {
        val selection = selectDailyQuote("2026-09-02", "2026-09-02", 8, setOf(1, 2, 3), randomValue = 999)

        assertEquals(8, selection.quoteIndex)
        assertEquals(setOf(1, 2, 3), selection.remainingIndices)
        assertFalse(selection.changed)
    }

    @Test fun `different dates consume every quote before repeating`() {
        var previousDate: String? = null
        var previousIndex: Int? = null
        var remaining = emptySet<Int>()
        val selected = mutableListOf<Int>()

        repeat(DAILY_QUOTES.size) { day ->
            val date = "2026-09-${(day + 1).toString().padStart(2, '0')}"
            val result = selectDailyQuote(date, previousDate, previousIndex, remaining, randomValue = day * 17)
            selected += result.quoteIndex
            previousDate = date
            previousIndex = result.quoteIndex
            remaining = result.remainingIndices
        }

        assertEquals(DAILY_QUOTES.size, selected.distinct().size)
        assertTrue(remaining.isEmpty())

        val next = selectDailyQuote("2026-10-02", previousDate, previousIndex, remaining, randomValue = previousIndex!!)
        assertNotEquals(previousIndex, next.quoteIndex)
    }
}
