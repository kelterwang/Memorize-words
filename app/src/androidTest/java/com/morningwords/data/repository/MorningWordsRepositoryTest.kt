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
import com.morningwords.domain.model.WrongWordStatus
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

    @Test fun summaryWordsOnlyContainErrorsFromTheCurrentRound() = runTest {
        val batch = repository.importBatch("统计", "apple 苹果\nbanana 香蕉")
        val id = (repository.createDailySession(listOf(batch), TestMode.STUDENT) as CreateSessionResult.Created).sessionId
        repository.answer(id, TestResult.UNKNOWN)
        val summary = repository.answer(id, TestResult.KNOW)
        assertEquals(listOf("apple"), summary.roundWrongWords)
        assertEquals(1, summary.roundWrongCount)
        repository.retryWrongAnswers(id)
        assertTrue(repository.loadSession(id)!!.roundWrongWords.isEmpty())
        val nextSummary = repository.answer(id, TestResult.KNOW)
        assertTrue(nextSummary.roundWrongWords.isEmpty())
        assertEquals(0, nextSummary.roundWrongCount)
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

    @Test fun deletingLastLibraryImmediatelyClearsWrongListAndDashboardButKeepsHistory() = runBlocking {
        val batch = repository.importBatch("待删除", "apple\nbanana\ncherry")
        val sessionId = (repository.createDailySession(listOf(batch), TestMode.STUDENT) as CreateSessionResult.Created).sessionId
        repeat(3) { repository.answer(sessionId, TestResult.UNKNOWN) }
        repository.completeFromSummary(sessionId)
        val history = repository.wrongWords.first().map { it.wrong }
        val session = database.dao().session(sessionId)
        val sessionWords = database.dao().sessionWords(sessionId)
        val counts = repository.dashboard.map { it.wrongCount }.distinctUntilChanged().produceIn(this)
        val lists = repository.wrongWords.map { it.size }.distinctUntilChanged().produceIn(this)
        try {
            assertEquals(3, withTimeout(5_000) { counts.receive() })
            assertEquals(3, withTimeout(5_000) { lists.receive() })
            assertTrue(repository.deleteBatch(batch))
            assertEquals(0, withTimeout(5_000) { counts.receive() })
            assertEquals(0, withTimeout(5_000) { lists.receive() })
            assertTrue(repository.wrongWordGroups.first().isEmpty())
            assertTrue(database.dao().activeWrongWords().isEmpty())
            assertEquals(CreateSessionResult.Empty, repository.createWrongReview(TestMode.STUDENT, listOf(batch)))
            assertEquals(session, database.dao().session(sessionId))
            assertEquals(sessionWords, database.dao().sessionWords(sessionId))
            history.forEach { assertEquals(it, database.dao().wrongWord(it.wordId)) }
            for (table in listOf("TestRecord", "WrongRecord")) {
                database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(3, cursor.getInt(0))
                }
            }
            // Fresh subscriptions must also hide orphaned wrong words from older versions.
            val reopened = MorningWordsRepository(database)
            assertEquals(0, reopened.dashboard.first().wrongCount)
            assertTrue(reopened.wrongWords.first().isEmpty())
        } finally {
            counts.cancel()
            lists.cancel()
        }
    }

    @Test fun deletingOneLibraryKeepsSharedWrongWordsAndCountsEachOnlyOnce() = runBlocking {
        val first = repository.importBatch("第一批", "apple\nbanana")
        val second = repository.importBatch("第二批", "banana\ncherry")
        val sessionId = (repository.createDailySession(listOf(first), TestMode.STUDENT) as CreateSessionResult.Created).sessionId
        repeat(2) { repository.answer(sessionId, TestResult.UNKNOWN) }
        repository.completeFromSummary(sessionId)
        val counts = repository.dashboard.map { it.wrongCount }.distinctUntilChanged().produceIn(this)
        val lists = repository.wrongWords.map { rows -> rows.map { it.word.word }.toSet() }.distinctUntilChanged().produceIn(this)
        try {
            assertEquals(2, withTimeout(5_000) { counts.receive() })
            assertEquals(setOf("apple", "banana"), withTimeout(5_000) { lists.receive() })
            assertTrue(repository.deleteBatch(first))
            assertEquals(1, withTimeout(5_000) { counts.receive() })
            assertEquals(setOf("banana"), withTimeout(5_000) { lists.receive() })
            assertEquals(listOf("banana"), database.dao().activeWrongWords().map { it.word.word })
            assertEquals(second, repository.wrongWordGroups.first().single().batchId)
            assertTrue(repository.deleteBatch(second))
            assertEquals(0, withTimeout(5_000) { counts.receive() })
            assertTrue(withTimeout(5_000) { lists.receive() }.isEmpty())
        } finally {
            counts.cancel()
            lists.cancel()
        }
    }

    @Test fun reimportingDeletedLibraryDoesNotReviveOldPendingWordsButNewErrorsDo() = runTest {
        val oldBatch = repository.importBatch("旧词库", "apple")
        val sessionId = (repository.createDailySession(listOf(oldBatch), TestMode.STUDENT) as CreateSessionResult.Created).sessionId
        repository.answer(sessionId, TestResult.UNKNOWN)
        repository.completeFromSummary(sessionId)
        val oldWrong = repository.wrongWords.first().single().wrong
        assertTrue(repository.deleteBatch(oldBatch))
        val newBatch = repository.importBatch("重新导入", "apple")
        assertEquals(oldWrong.wordId, repository.wordsInBatch(newBatch).single().id)
        assertEquals(0, repository.dashboard.first().wrongCount)
        assertTrue(repository.wrongWords.first().isEmpty())
        assertTrue(repository.wrongWordGroups.first().isEmpty())
        assertTrue(database.dao().activeWrongWords().isEmpty())
        assertEquals(CreateSessionResult.Empty, repository.createWrongReview(TestMode.STUDENT, listOf(newBatch)))
        assertEquals(oldWrong, database.dao().wrongWord(oldWrong.wordId))

        val newSession = (repository.createDailySession(listOf(newBatch), TestMode.STUDENT) as CreateSessionResult.Created).sessionId
        repository.answer(newSession, TestResult.UNKNOWN)
        repository.completeFromSummary(newSession)
        assertEquals(1, repository.dashboard.first().wrongCount)
        assertEquals("apple", repository.wrongWords.first().single().word.word)
        assertEquals(newBatch, repository.wrongWordGroups.first().single().batchId)
        assertEquals(2, database.dao().wrongWord(oldWrong.wordId)?.wrongCount)
        assertTrue(repository.createWrongReview(TestMode.STUDENT, listOf(newBatch)) is CreateSessionResult.Created)
    }

    @Test fun folderReviewIncludesYearOldErrorsDeduplicatesAndExcludesMasteredOrUnselectedWords() = runTest {
        val first = repository.importBatch("旧错词", "ancient\nshared\nmastered")
        val second = repository.importBatch("最近错词", "shared\nrecent")
        val unselected = repository.importBatch("未选择", "outside")
        suspend fun failBatch(batch: Long) {
            val session = (repository.createDailySession(listOf(batch), TestMode.STUDENT) as CreateSessionResult.Created).sessionId
            repeat(repository.wordsInBatch(batch).size) { repository.answer(session, TestResult.UNKNOWN) }
            repository.completeFromSummary(session)
        }
        failBatch(first)
        val mastered = checkNotNull(database.dao().findWord("mastered"))
        val wrong = checkNotNull(database.dao().wrongWord(mastered.id))
        database.dao().updateWrongWord(wrong.copy(status = WrongWordStatus.MASTERED, masteredAt = now++))
        now += 365L * 86_400_000L
        failBatch(second)
        failBatch(unselected)

        val singleFolder = (repository.createWrongReview(TestMode.PARENT, listOf(first)) as CreateSessionResult.Created).sessionId
        assertEquals(setOf("ancient", "shared"), database.dao().sessionWords(singleFolder).map { it.word.word }.toSet())
        assertEquals(TestMode.PARENT, repository.loadSession(singleFolder)?.session?.mode)
        repository.abandon(singleFolder)

        val combined = (repository.createWrongReview(TestMode.STUDENT, listOf(first, second, first)) as CreateSessionResult.Created).sessionId
        assertEquals(3, repository.loadSession(combined)?.session?.totalCount)
        assertEquals(listOf("recent", "shared", "ancient"), database.dao().sessionWords(combined).map { it.word.word })
        assertEquals(TestMode.STUDENT, repository.loadSession(combined)?.session?.mode)
        repository.abandon(combined)
        assertEquals(CreateSessionResult.Empty, repository.createWrongReview(TestMode.STUDENT, emptyList()))
    }
}
