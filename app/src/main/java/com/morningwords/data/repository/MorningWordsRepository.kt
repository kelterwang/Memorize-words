package com.morningwords.data.repository

import androidx.room.withTransaction
import com.morningwords.data.dao.MorningWordsDao
import com.morningwords.data.entity.*
import com.morningwords.data.local.AppDatabase
import com.morningwords.domain.importer.ImportPreview
import com.morningwords.domain.importer.WordImporter
import com.morningwords.domain.model.*
import com.morningwords.domain.statemachine.TestStateMachine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

data class Dashboard(val batchCount: Int, val wordCount: Int, val wrongCount: Int)
data class SessionView(
    val session: SessionSnapshot,
    val current: WordCard?,
    val passedCount: Int,
    val pendingCount: Int,
)

sealed interface CreateSessionResult {
    data class Created(val sessionId: Long) : CreateSessionResult
    data class AlreadyInProgress(val sessionId: Long) : CreateSessionResult
    data object Empty : CreateSessionResult
}

class MorningWordsRepository(
    private val db: AppDatabase,
    private val dao: MorningWordsDao = db.dao(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val machine: TestStateMachine = TestStateMachine(),
) {
    val batches: Flow<List<BatchSummary>> = dao.observeBatches().mapRows()
    val wrongWords: Flow<List<WrongWordRow>> = dao.observeWrongWords()
    val dashboard: Flow<Dashboard> = combine(
        dao.observeBatchCount(), dao.observeWordCount(), dao.observeWrongCount()
    ) { batches, words, wrong -> Dashboard(batches, words, wrong) }

    fun previewImport(text: String): ImportPreview = WordImporter.parseText(text)

    suspend fun importBatch(name: String, text: String): Long = db.withTransaction {
        val preview = WordImporter.parseText(text)
        require(name.isNotBlank()) { "请输入批次名称" }
        require(preview.accepted.isNotEmpty()) { "没有可导入的单词" }
        require(preview.errorCount == 0) { "请先修正错误行" }
        val now = clock()
        val batchId = dao.insertBatch(WordBatchEntity(batchName = name.trim(), createdAt = now, source = "TEXT"))
        preview.accepted.forEachIndexed { order, line ->
            val normalized = checkNotNull(line.normalizedWord)
            val incomingWord = checkNotNull(line.word)
            val existing = dao.findWord(normalized)
            val wordId = if (existing == null) {
                dao.insertWord(
                    WordEntity(
                        word = incomingWord,
                        normalizedWord = normalized,
                        partOfSpeech = line.partOfSpeech,
                        meaning = line.meaning,
                        createdAt = now,
                        updatedAt = now,
                    )
                )
            } else {
                val merged = existing.copy(
                    partOfSpeech = existing.partOfSpeech ?: line.partOfSpeech,
                    meaning = existing.meaning ?: line.meaning,
                    updatedAt = now,
                )
                if (merged != existing) dao.updateWord(merged)
                existing.id
            }
            dao.insertBatchWord(
                BatchWordEntity(
                    batchId = batchId,
                    wordId = wordId,
                    requiredMeaning = line.requiredMeaning,
                    sortOrder = order,
                    rawText = line.rawText,
                )
            )
        }
        batchId
    }

    suspend fun deleteBatch(batchId: Long): Boolean = db.withTransaction {
        if (dao.activeBatchReferences(batchId) > 0) return@withTransaction false
        dao.detachHistoricBatch(batchId)
        dao.deleteBatchRow(batchId)
        true
    }

    suspend fun createDailySession(batchIds: List<Long>, mode: TestMode): CreateSessionResult = db.withTransaction {
        dao.activeSession()?.let { return@withTransaction CreateSessionResult.AlreadyInProgress(it.id) }
        val orderedBatchIds = batchIds.distinct()
        val candidates = orderedBatchIds.flatMap { dao.batchWords(it) }
        val unique = candidates.distinctBy { it.wordId }
        if (unique.isEmpty()) return@withTransaction CreateSessionResult.Empty
        val now = clock()
        val sessionId = dao.insertSession(
            TestSessionEntity(
                sessionType = SessionType.DAILY_TEST,
                testMode = mode,
                status = SessionStatus.IN_PROGRESS,
                phase = TestPhase.FIRST_ROUND,
                createdAt = now,
                updatedAt = now,
                totalCount = unique.size,
            )
        )
        dao.insertSessionBatches(orderedBatchIds.mapIndexed { index, id -> SessionBatchEntity(sessionId = sessionId, batchId = id, selectionOrder = index) })
        dao.insertSessionWords(unique.mapIndexed { index, item ->
            SessionWordEntity(
                sessionId = sessionId,
                wordId = item.wordId,
                displayOrder = index,
                requiredMeaningSnapshot = item.requiredMeaning,
                queueState = SessionWordQueueState.NOT_ANSWERED,
                queueOrder = index,
            )
        })
        CreateSessionResult.Created(sessionId)
    }

    suspend fun createWrongReview(start: Long, end: Long, mode: TestMode): CreateSessionResult = db.withTransaction {
        dao.activeSession()?.let { return@withTransaction CreateSessionResult.AlreadyInProgress(it.id) }
        val wordIds = dao.reviewWordIds(start, end)
        if (wordIds.isEmpty()) return@withTransaction CreateSessionResult.Empty
        val now = clock()
        val sessionId = dao.insertSession(
            TestSessionEntity(
                sessionType = SessionType.WRONG_REVIEW,
                testMode = mode,
                status = SessionStatus.IN_PROGRESS,
                phase = TestPhase.WRONG_REVIEW,
                createdAt = now,
                updatedAt = now,
                totalCount = wordIds.size,
                roundNumber = 1,
            )
        )
        dao.insertSessionWords(wordIds.mapIndexed { index, id ->
            SessionWordEntity(sessionId = sessionId, wordId = id, displayOrder = index, requiredMeaningSnapshot = null, queueState = SessionWordQueueState.QUEUED, queueOrder = index)
        })
        CreateSessionResult.Created(sessionId)
    }

    suspend fun activeSessionId(): Long? = dao.activeSession()?.id

    suspend fun loadSession(sessionId: Long): SessionView? {
        val entity = dao.session(sessionId) ?: return null
        val rows = dao.sessionWords(sessionId)
        val snapshot = entity.snapshot()
        val currentRow = findCurrent(snapshot, rows)
        val passed = rows.count { it.sessionWord.queueState == SessionWordQueueState.PASSED }
        return SessionView(
            session = snapshot,
            current = currentRow?.let { row ->
                WordCard(
                    id = row.word.id,
                    word = row.word.word,
                    phonetic = row.word.phonetic,
                    partOfSpeech = row.word.partOfSpeech,
                    meaning = row.word.meaning,
                    requiredMeaning = row.sessionWord.requiredMeaningSnapshot,
                    example = row.word.example,
                )
            },
            passedCount = passed,
            pendingCount = entity.totalCount - passed,
        )
    }

    suspend fun answer(sessionId: Long, result: TestResult): SessionView = db.withTransaction {
        val sessionEntity = requireNotNull(dao.session(sessionId))
        val rows = dao.sessionWords(sessionId)
        val snapshot = sessionEntity.snapshot()
        val currentRow = requireNotNull(findCurrent(snapshot, rows)) { "没有待作答单词" }
        val current = currentRow.sessionWord.snapshot()
        val plan = machine.transition(snapshot, current, rows.map { it.sessionWord.snapshot() }, result)
        val now = clock()

        dao.insertTestRecord(
            TestRecordEntity(
                sessionId = sessionId,
                wordId = current.wordId,
                phase = sessionEntity.phase,
                roundNumber = sessionEntity.roundNumber,
                result = result,
                testMode = sessionEntity.testMode,
                createdAt = now,
            )
        )

        val updatedCurrent = currentRow.sessionWord.copy(
            wasFirstRoundWrong = plan.currentWasFirstWrong,
            queueState = plan.currentState,
            lastAnsweredPhase = sessionEntity.phase,
            lastAnsweredRound = sessionEntity.roundNumber,
            lastResult = result,
        )
        dao.updateSessionWord(updatedCurrent)

        if (plan.resetFinalCheck) {
            val refreshed = dao.sessionWords(sessionId).filter { it.sessionWord.wasFirstRoundWrong }
                .mapIndexed { index, row -> row.sessionWord.copy(queueState = SessionWordQueueState.QUEUED, queueOrder = index, lastAnsweredRound = null) }
            dao.updateSessionWords(refreshed)
        }

        applyWrongWordRules(sessionEntity, current.wordId, result, now)
        dao.updateSession(
            sessionEntity.copy(
                status = plan.status,
                phase = if (plan.status == SessionStatus.COMPLETED) TestPhase.COMPLETED else plan.phase,
                updatedAt = now,
                completedAt = if (plan.status == SessionStatus.COMPLETED) now else null,
                firstPassCount = sessionEntity.firstPassCount + plan.firstPassDelta,
                firstWrongCount = sessionEntity.firstWrongCount + plan.firstWrongDelta,
                roundNumber = plan.roundNumber,
            )
        )
        requireNotNull(loadSession(sessionId))
    }

    private suspend fun applyWrongWordRules(session: TestSessionEntity, wordId: Long, result: TestResult, now: Long) {
        val existing = dao.wrongWord(wordId)
        if (session.sessionType == SessionType.DAILY_TEST && session.phase == TestPhase.FIRST_ROUND && result == TestResult.UNKNOWN) {
            if (existing == null) {
                dao.insertWrongWord(WrongWordEntity(wordId = wordId, firstWrongAt = now, lastWrongAt = now, wrongCount = 1))
            } else {
                dao.updateWrongWord(existing.copy(lastWrongAt = now, wrongCount = existing.wrongCount + 1, status = WrongWordStatus.ACTIVE, masteredAt = null))
            }
            dao.insertWrongRecord(WrongRecordEntity(wordId = wordId, testSessionId = session.id, wrongAt = now, recordType = WrongRecordType.FIRST_ROUND_WRONG, testMode = session.testMode))
        }
        if (session.sessionType == SessionType.WRONG_REVIEW) {
            when (result) {
                TestResult.UNKNOWN -> if (existing != null) {
                    dao.updateWrongWord(existing.copy(lastWrongAt = now, reviewWrongCount = existing.reviewWrongCount + 1, status = WrongWordStatus.ACTIVE, masteredAt = null))
                    dao.insertWrongRecord(WrongRecordEntity(wordId = wordId, testSessionId = session.id, wrongAt = now, recordType = WrongRecordType.WRONG_REVIEW_WRONG, testMode = session.testMode))
                }
                TestResult.MASTERED -> if (existing != null) dao.updateWrongWord(existing.copy(status = WrongWordStatus.MASTERED, masteredAt = now))
                TestResult.KNOW -> Unit
            }
        }
    }

    suspend fun abandon(sessionId: Long) = dao.abandon(sessionId, clock())

    private fun findCurrent(session: SessionSnapshot, rows: List<SessionWordWithWord>): SessionWordWithWord? = when (session.phase) {
        TestPhase.FIRST_ROUND -> rows.firstOrNull { it.sessionWord.queueState == SessionWordQueueState.NOT_ANSWERED }
        TestPhase.WRONG_LOOP, TestPhase.FINAL_CHECK, TestPhase.WRONG_REVIEW -> rows.firstOrNull {
            it.sessionWord.queueState == SessionWordQueueState.QUEUED && (it.sessionWord.lastAnsweredRound ?: -1) < session.roundNumber
        }
        TestPhase.COMPLETED -> null
    }
}

private fun TestSessionEntity.snapshot() = SessionSnapshot(id, sessionType, testMode, status, phase, totalCount, firstPassCount, firstWrongCount, roundNumber)
private fun SessionWordEntity.snapshot() = SessionWordSnapshot(id, wordId, displayOrder, wasFirstRoundWrong, queueState, queueOrder, lastAnsweredRound, lastResult)
private fun Flow<List<BatchRow>>.mapRows(): Flow<List<BatchSummary>> = map { rows -> rows.map { BatchSummary(it.id, it.batchName, it.wordCount, it.createdAt) } }
