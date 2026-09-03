package com.morningwords.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.morningwords.data.local.AppDatabase
import com.morningwords.data.entity.WordEntity
import com.morningwords.data.entity.WordBatchEntity
import com.morningwords.data.entity.BatchWordEntity
import com.morningwords.domain.model.SessionStatus
import com.morningwords.domain.model.TestMode
import com.morningwords.domain.model.TestPhase
import com.morningwords.domain.model.TestResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MorningWordsRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: MorningWordsRepository
    private var now = 1_700_000_000_000L

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repository = MorningWordsRepository(database, clock = { now++ })
    }

    @After fun tearDown() = database.close()

    @Test fun wordsAndPhrasesPersistSeparatelyAndOnlyExactPhrasesDeduplicate() = runTest {
        val first = repository.importBatch("短语", "look v. 看\nlook forward to 期待\nlook after 照顾\n LOOK   forward TO 期待")
        val second = repository.importBatch("第二批", "LOOK forward TO 期待\nlook into 调查")
        assertEquals(listOf("look", "look forward to", "look after"), repository.wordsInBatch(first).map { it.word })
        assertEquals(4, repository.dashboard.first().wordCount)
        val firstPhrase = repository.wordsInBatch(first)[1]
        assertEquals(firstPhrase.id, repository.wordsInBatch(second)[0].id)
        val sessionId = (repository.createDailySession(listOf(first, second), TestMode.STUDENT) as CreateSessionResult.Created).sessionId
        assertEquals(4, repository.loadSession(sessionId)?.session?.totalCount)
        assertEquals(setOf("look", "look forward to", "look after", "look into"), database.dao().sessionWords(sessionId).map { it.word.word }.toSet())
    }

    private suspend fun legacyBatch(name: String, wordId: Long, raw: String): Long {
        val batch = database.dao().insertBatch(WordBatchEntity(batchName = name, createdAt = now++))
        database.dao().insertBatchWord(BatchWordEntity(batchId = batch, wordId = wordId, requiredMeaning = "旧释义", sortOrder = 0, rawText = raw))
        return batch
    }

    @Test fun repairsTruncatedPhraseInPlaceAndKeepsLearningHistory() = runTest {
        val id = database.dao().insertWord(WordEntity(word = "one", normalizedWord = "one", meaning = "by one 依次地", createdAt = now++, updatedAt = now++))
        val batch = legacyBatch("旧短语", id, "one by one 依次地 Come to the front one by one. (P3)")
        val sessionId = (repository.createDailySession(listOf(batch), TestMode.STUDENT) as CreateSessionResult.Created).sessionId
        repository.answer(sessionId, TestResult.UNKNOWN)
        val wrongBefore = repository.wrongWords.first().single().wrong
        val sessionBefore = database.dao().session(sessionId)
        repository.repairImportedWordFields()
        val repaired = repository.wordsInBatch(batch).single()
        assertEquals(id, repaired.id)
        assertEquals("one by one", repaired.word)
        assertEquals("one by one", repaired.normalizedWord)
        assertEquals("依次地", repaired.meaning)
        assertEquals("依次地", database.dao().batchWords(batch).single().requiredMeaning)
        assertEquals("Come to the front one by one. (P3)", repaired.example)
        assertEquals(wrongBefore, repository.wrongWords.first().single().wrong)
        assertEquals(sessionBefore, database.dao().session(sessionId))
        repository.repairImportedWordFields()
        assertEquals(repaired, repository.wordsInBatch(batch).single())
    }

    @Test fun splitsLegacySharedWordIntoDistinctLibraryPhrasesWithoutMovingHistory() = runTest {
        val id = database.dao().insertWord(WordEntity(word = "look", normalizedWord = "look", meaning = "看", createdAt = now++, updatedAt = now++))
        val wordBatch = legacyBatch("单词", id, "look v. 看")
        val phraseBatch = legacyBatch("短语", id, "look forward to 期待")
        val otherPhraseBatch = legacyBatch("另一短语", id, "look after 照顾")
        val sessionId = (repository.createDailySession(listOf(wordBatch), TestMode.STUDENT) as CreateSessionResult.Created).sessionId
        repository.answer(sessionId, TestResult.UNKNOWN)
        val wrongBefore = repository.wrongWords.first().single().wrong
        repository.repairImportedWordFields()
        assertEquals(id, repository.wordsInBatch(wordBatch).single().id)
        assertEquals("look forward to", repository.wordsInBatch(phraseBatch).single().word)
        assertEquals("look after", repository.wordsInBatch(otherPhraseBatch).single().word)
        assertEquals(3, repository.dashboard.first().wordCount)
        assertEquals(wrongBefore, repository.wrongWords.first().single().wrong)
        repository.repairImportedWordFields()
        assertEquals(3, repository.dashboard.first().wordCount)
    }

    @Test fun existingFullPhraseIsReusedWhenRepairingTruncatedIdentity() = runTest {
        val batch = repository.importBatch("正确短语", "look forward to 期待")
        val target = repository.wordsInBatch(batch).single()
        val old = database.dao().insertWord(WordEntity(word = "look", normalizedWord = "look", createdAt = now++, updatedAt = now++))
        database.dao().insertBatchWord(BatchWordEntity(batchId = batch, wordId = old, requiredMeaning = "旧释义", sortOrder = 1, rawText = "look forward to 期待"))
        val other = legacyBatch("旧短语", old, "look forward to 期待")
        repository.repairImportedWordFields()
        assertEquals(target.id, repository.wordsInBatch(batch).single().id)
        assertEquals(target.id, repository.wordsInBatch(other).single().id)
        assertNotNull(database.dao().word(old))
        repository.repairImportedWordFields()
        assertEquals(1, repository.dashboard.first().wordCount)
    }

    @Test fun importDeduplicatesAcrossBatchesAndCountsSharedWordsOncePerSession() = runTest {
        val first = repository.importBatch("第一批", "Maintain v. 保持\nachieve v. 实现")
        val second = repository.importBatch("第二批", " maintain v. 维持\navailable adj. 可用的")
        val dashboard = repository.dashboard.first()
        assertEquals(3, dashboard.wordCount)

        val result = repository.createDailySession(listOf(first, second), TestMode.STUDENT)
        val sessionId = (result as CreateSessionResult.Created).sessionId
        assertEquals(3, repository.loadSession(sessionId)?.session?.totalCount)
    }

    @Test fun firstRoundShowsStatsAndWrongAnswersRetryOnlyWhenRequested() = runTest {
        val batch = repository.importBatch("测试", "achieve\nmaintain")
        val sessionId = (repository.createDailySession(listOf(batch), TestMode.STUDENT) as CreateSessionResult.Created).sessionId

        var state = repository.answer(sessionId, TestResult.UNKNOWN)
        assertEquals(TestPhase.FIRST_ROUND, state.session.phase)
        assertEquals(1, state.roundTestedCount)
        assertEquals(0, state.roundKnownCount)
        assertEquals(1, state.roundWrongCount)
        state = repository.answer(sessionId, TestResult.KNOW)
        assertEquals(TestPhase.ROUND_SUMMARY, state.session.phase)
        assertEquals(2, state.roundTestedCount)
        assertEquals(1, state.roundKnownCount)
        assertEquals(1, state.roundWrongCount)
        assertEquals(1, repository.wrongWords.first().size)

        state = repository.retryWrongAnswers(sessionId)
        assertEquals(TestPhase.WRONG_LOOP, state.session.phase)
        assertEquals(1, state.roundTotalCount)
        assertEquals(0, state.roundTestedCount)
        state = repository.answer(sessionId, TestResult.KNOW)
        assertEquals(TestPhase.ROUND_SUMMARY, state.session.phase)
        assertEquals(1, state.roundKnownCount)
        assertEquals(0, state.roundWrongCount)
        state = repository.completeFromSummary(sessionId)
        assertEquals(SessionStatus.COMPLETED, state.session.status)
        assertEquals(1, repository.wrongWords.first().single().wrong.wrongCount)
    }

    @Test fun failedRetryCanBeRequestedRepeatedly() = runTest {
        val batch = repository.importBatch("测试", "achieve")
        val sessionId = (repository.createDailySession(listOf(batch), TestMode.STUDENT) as CreateSessionResult.Created).sessionId

        repository.answer(sessionId, TestResult.UNKNOWN)
        repository.retryWrongAnswers(sessionId)
        var state = repository.answer(sessionId, TestResult.UNKNOWN)
        assertEquals(TestPhase.ROUND_SUMMARY, state.session.phase)
        assertEquals(1, state.roundWrongCount)

        state = repository.retryWrongAnswers(sessionId)
        assertEquals(2, state.session.roundNumber)
        state = repository.answer(sessionId, TestResult.KNOW)
        assertEquals(TestPhase.ROUND_SUMMARY, state.session.phase)
        assertEquals(0, state.roundWrongCount)
    }

    @Test fun activeSessionProtectsItsSourceBatch() = runTest {
        val batch = repository.importBatch("不可删", "achieve")
        repository.createDailySession(listOf(batch), TestMode.PARENT)
        assertTrue(!repository.deleteBatch(batch))
    }

    @Test fun staleDeletedBatchSelectionIsIgnoredWhenStarting() = runTest {
        val deletedBatch = repository.importBatch("已删除", "obsolete")
        assertTrue(repository.deleteBatch(deletedBatch))
        val currentBatch = repository.importBatch("当前", "achieve\nmaintain")

        val result = repository.createDailySession(listOf(deletedBatch, currentBatch), TestMode.STUDENT)

        val sessionId = (result as CreateSessionResult.Created).sessionId
        assertEquals(2, repository.loadSession(sessionId)?.session?.totalCount)
    }

    @Test fun onlyStaleDeletedSelectionsProduceEmptyResult() = runTest {
        val deletedBatch = repository.importBatch("已删除", "obsolete")
        assertTrue(repository.deleteBatch(deletedBatch))

        assertEquals(CreateSessionResult.Empty, repository.createDailySession(listOf(deletedBatch), TestMode.STUDENT))
    }

    @Test fun deletingBatchUpdatesDashboardWordCountAndKeepsSharedWords() = runBlocking {
        val first = repository.importBatch("第一批", "apple\nbanana")
        val second = repository.importBatch("第二批", "banana\ncherry")
        val counts = repository.dashboard.map { it.wordCount }.distinctUntilChanged().produceIn(this)
        try {
            assertEquals(3, withTimeout(5_000) { counts.receive() })

            assertTrue(repository.deleteBatch(first))
            assertEquals(2, withTimeout(5_000) { counts.receive() })
            assertNotNull(database.dao().findWord("apple"))

            assertTrue(repository.deleteBatch(second))
            assertEquals(0, withTimeout(5_000) { counts.receive() })
        } finally {
            counts.cancel()
        }
    }
}
