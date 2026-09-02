package com.morningwords.domain.model

enum class SessionType { DAILY_TEST, WRONG_REVIEW }
enum class TestMode { STUDENT, PARENT }
enum class SessionStatus { IN_PROGRESS, COMPLETED, ABANDONED }
enum class TestPhase { FIRST_ROUND, WRONG_LOOP, FINAL_CHECK, WRONG_REVIEW, ROUND_SUMMARY, COMPLETED }
enum class TestResult { KNOW, UNKNOWN, MASTERED }
enum class WrongWordStatus { ACTIVE, MASTERED }
enum class WrongRecordType { FIRST_ROUND_WRONG, WRONG_REVIEW_WRONG }
enum class SessionWordQueueState { NOT_ANSWERED, QUEUED, PASSED }

data class WordCard(
    val id: Long,
    val word: String,
    val phonetic: String?,
    val partOfSpeech: String?,
    val meaning: String?,
    val requiredMeaning: String?,
    val example: String?,
)

data class BatchSummary(val id: Long, val name: String, val wordCount: Int, val createdAt: Long)

data class SessionSnapshot(
    val id: Long,
    val type: SessionType,
    val mode: TestMode,
    val status: SessionStatus,
    val phase: TestPhase,
    val totalCount: Int,
    val firstPassCount: Int,
    val firstWrongCount: Int,
    val roundNumber: Int,
)

data class SessionWordSnapshot(
    val id: Long,
    val wordId: Long,
    val displayOrder: Int,
    val wasFirstRoundWrong: Boolean,
    val queueState: SessionWordQueueState,
    val queueOrder: Int,
    val lastAnsweredRound: Int?,
    val lastResult: TestResult?,
)

data class TransitionPlan(
    val phase: TestPhase,
    val status: SessionStatus,
    val roundNumber: Int,
    val firstPassDelta: Int = 0,
    val firstWrongDelta: Int = 0,
    val currentState: SessionWordQueueState,
    val currentWasFirstWrong: Boolean,
    val resetFinalCheck: Boolean = false,
)
