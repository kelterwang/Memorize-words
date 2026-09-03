package com.morningwords.domain.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WordImporterTest {
    @Test fun `imports words and phrases sharing the first word independently`() {
        val preview = WordImporter.parseText("""
            look v. 看 Look at the sky.
            look forward to （兴奋地）期待，盼望 I'm looking forward to it! (P14)
            look after phr. 照顾 Look after yourself.
            in prep. 在……里面
            in panic 惊慌地 I looked at them in panic. (P4)
            in particular 尤其
            go v. 去
            go all out 全力以赴，竭尽全力 But then I figured I'd better just go all out. (P11)
        """.trimIndent())
        assertEquals(0, preview.duplicateCount)
        assertEquals(0, preview.errorCount)
        assertEquals(listOf("look", "look forward to", "look after", "in", "in panic", "in particular", "go", "go all out"), preview.accepted.map { it.word })
        assertEquals("（兴奋地）期待，盼望", preview.accepted[1].meaning)
        assertEquals("I'm looking forward to it! (P14)", preview.accepted[1].example)
        assertEquals("phr", preview.accepted[2].partOfSpeech)
    }

    @Test fun `normalizes the entire phrase before duplicate detection`() {
        val preview = WordImporter.parseText("look forward to\n LOOK   forward  TO 期待\nlook after\nbutterflies in one's stomach\nButterflies in one’s stomach 情绪紧张")
        assertEquals(3, preview.accepted.size)
        assertEquals(2, preview.duplicateCount)
        assertEquals("LOOK forward TO", preview.lines[1].word)
        assertEquals("look forward to", preview.lines[1].normalizedWord)
        assertEquals("butterflies in one's stomach", preview.lines[4].normalizedWord)
    }

    @Test fun `handles bare phrases POS and attached Chinese meanings`() {
        val preview = WordImporter.parseText("one by one\none after another依次地\nlook forward to phr.期待\nwell-known adj. 著名的")
        assertEquals(listOf("one by one", "one after another", "look forward to", "well-known"), preview.accepted.map { it.word })
        assertNull(preview.accepted[0].meaning)
        assertEquals("依次地", preview.accepted[1].meaning)
        assertEquals("期待", preview.accepted[2].meaning)
        assertEquals("phr", preview.accepted[2].partOfSpeech)
    }

    @Test fun `keeps tab separated English definitions out of phrases`() {
        val line = WordImporter.parseText("look after\ttake care of someone").accepted.single()
        assertEquals("look after", line.word)
        assertEquals("take care of someone", line.meaning)
    }

    @Test fun `rejects invalid entry characters instead of accepting a partial word`() {
        val preview = WordImporter.parseText("abc123 释义\nfoo_bar 释义\n12345 ???")
        assertEquals(3, preview.errorCount)
        assertEquals(0, preview.accepted.size)
    }

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

    @Test fun `separates textbook examples and preserves page references`() {
        val cases = listOf(
            Triple("senior adj. （地位、水平或级别）高的，高级的 Welcome to senior high! (P1)", "（地位、水平或级别）高的，高级的", "Welcome to senior high! (P1)"),
            Triple("curious adj. 好奇的How did you feel on your first day at senior high? curious  (P1)", "好奇的", "How did you feel on your first day at senior high? curious  (P1)"),
            Triple("impression n. 印象，感想What was your first impression of your new school? (P1)", "印象，感想", "What was your first impression of your new school? (P1)"),
            Triple("confident adj. 有信心的，自信的  ...he felt more confident. (P4)", "有信心的，自信的", "...he felt more confident. (P4)"),
            Triple("hint n. 有益的建议 High School Hints (P10)", "有益的建议", "High School Hints (P10)"),
            Triple("subscribe v. 订阅（报纸或杂志） Subscribed 3,101 (P10)", "订阅（报纸或杂志）", "Subscribed 3,101 (P10)"),
            Triple("view n. （一次）观看 1,231 views (P11)", "（一次）观看", "1,231 views (P11)"),
            Triple("memorise v. 记住，熟记 \"Thinking is a more important ability than memorising,\" he said. (P14)", "记住，熟记", "\"Thinking is a more important ability than memorising,\" he said. (P14)"),
            Triple("exchange v. 交流（信息、想法等） exchange ideas (P16)", "交流（信息、想法等）", "exchange ideas (P16)"),
            Triple("improve v. 改善，改进 …think about the changes （P16）", "改善，改进", "…think about the changes （P16）"),
        )
        cases.forEach { (raw, meaning, example) ->
            val line = WordImporter.parseText(raw).accepted.single()
            assertEquals(raw, meaning, line.meaning)
            assertEquals(raw, meaning, line.requiredMeaning)
            assertEquals(raw, example, line.example)
        }
    }

    @Test fun `accepts examples without punctuation and without a separating space`() {
        val line = WordImporter.parseText("campus n. 校园the campus was quiet").accepted.single()
        assertEquals("校园", line.meaning)
        assertEquals("the campus was quiet", line.example)
    }

    @Test fun `keeps English terms and parenthetical translations inside definitions`() {
        listOf("维生素 C", "银行 (bank)", "脱氧核糖核酸 DNA", "与 DNA 有关的", "一种设备 (P1)").forEach { meaning ->
            val line = WordImporter.parseText("term n. $meaning").accepted.single()
            assertEquals(meaning, line.meaning)
            assertNull(meaning, line.example)
        }
    }
}
