package com.morningwords.domain.statemachine

import com.morningwords.domain.model.*

/** Pure business transition logic. Persistence and queue ordering stay outside this class. */
class TestStateMachine {
    fun transition(
        session: SessionSnapshot,
        current: SessionWordSnapshot,
        allWords: List<SessionWordSnapshot>,
        result: TestResult,
    ): TransitionPlan {
        require(session.status == SessionStatus.IN_PROGRESS) { "Session is not answerable" }
        require(session.phase != TestPhase.COMPLETED) { "Completed session cannot be answered" }
        require(!(session.type == SessionType.DAILY_TEST && result == TestResult.MASTERED)) {
            "MASTERED is only valid in wrong-word review"
        }
        require(!(session.type == SessionType.WRONG_REVIEW && session.phase != TestPhase.WRONG_REVIEW)) {
            "Wrong review session has an invalid phase"
        }

        return when (session.phase) {
            TestPhase.FIRST_ROUND -> firstRound(session, current, allWords, result)
            TestPhase.WRONG_LOOP -> wrongLoop(session, current, allWords, result)
            TestPhase.FINAL_CHECK -> finalCheck(session, current, allWords, result)
            TestPhase.WRONG_REVIEW -> wrongReview(session, current, allWords, result)
            TestPhase.COMPLETED -> error("Completed session cannot be answered")
        }
    }

    private fun firstRound(
        session: SessionSnapshot,
        current: SessionWordSnapshot,
        words: List<SessionWordSnapshot>,
        result: TestResult,
    ): TransitionPlan {
        require(result != TestResult.MASTERED)
        val wrong = result == TestResult.UNKNOWN
        val remaining = words.count { it.queueState == SessionWordQueueState.NOT_ANSWERED } - 1
        val totalWrong = session.firstWrongCount + if (wrong) 1 else 0
        val finished = remaining == 0
        return TransitionPlan(
            phase = when {
                !finished -> TestPhase.FIRST_ROUND
                totalWrong == 0 -> TestPhase.COMPLETED
                else -> TestPhase.WRONG_LOOP
            },
            status = if (finished && totalWrong == 0) SessionStatus.COMPLETED else SessionStatus.IN_PROGRESS,
            roundNumber = if (finished && totalWrong > 0) 1 else session.roundNumber,
            firstPassDelta = if (wrong) 0 else 1,
            firstWrongDelta = if (wrong) 1 else 0,
            currentState = if (wrong) SessionWordQueueState.QUEUED else SessionWordQueueState.PASSED,
            currentWasFirstWrong = wrong || current.wasFirstRoundWrong,
        )
    }

    private fun wrongLoop(
        session: SessionSnapshot,
        current: SessionWordSnapshot,
        words: List<SessionWordSnapshot>,
        result: TestResult,
    ): TransitionPlan {
        require(result != TestResult.MASTERED)
        val wrong = result == TestResult.UNKNOWN
        val remainingThisRound = words.count {
            it.queueState == SessionWordQueueState.QUEUED && (it.lastAnsweredRound ?: -1) < session.roundNumber
        } - 1
        val otherQueuedAfter = words.any {
            it.id != current.id && it.queueState == SessionWordQueueState.QUEUED && it.lastAnsweredRound == session.roundNumber
        }
        val hasWrong = wrong || otherQueuedAfter
        val roundFinished = remainingThisRound == 0
        return TransitionPlan(
            phase = when {
                !roundFinished -> TestPhase.WRONG_LOOP
                hasWrong -> TestPhase.WRONG_LOOP
                else -> TestPhase.FINAL_CHECK
            },
            status = SessionStatus.IN_PROGRESS,
            roundNumber = if (roundFinished) session.roundNumber + 1 else session.roundNumber,
            currentState = if (wrong) SessionWordQueueState.QUEUED else SessionWordQueueState.PASSED,
            currentWasFirstWrong = current.wasFirstRoundWrong,
            resetFinalCheck = roundFinished && !hasWrong,
        )
    }

    private fun finalCheck(
        session: SessionSnapshot,
        current: SessionWordSnapshot,
        words: List<SessionWordSnapshot>,
        result: TestResult,
    ): TransitionPlan {
        require(result != TestResult.MASTERED)
        val wrong = result == TestResult.UNKNOWN
        val remaining = words.count {
            it.wasFirstRoundWrong && it.queueState == SessionWordQueueState.QUEUED &&
                (it.lastAnsweredRound ?: -1) < session.roundNumber
        } - 1
        val otherWrong = words.any {
            it.id != current.id && it.queueState == SessionWordQueueState.QUEUED &&
                it.lastAnsweredRound == session.roundNumber
        }
        val hasWrong = wrong || otherWrong
        val finished = remaining == 0
        return TransitionPlan(
            phase = when {
                !finished -> TestPhase.FINAL_CHECK
                hasWrong -> TestPhase.WRONG_LOOP
                else -> TestPhase.COMPLETED
            },
            status = if (finished && !hasWrong) SessionStatus.COMPLETED else SessionStatus.IN_PROGRESS,
            roundNumber = if (finished && hasWrong) session.roundNumber + 1 else session.roundNumber,
            currentState = if (wrong) SessionWordQueueState.QUEUED else SessionWordQueueState.PASSED,
            currentWasFirstWrong = current.wasFirstRoundWrong,
        )
    }

    private fun wrongReview(
        session: SessionSnapshot,
        current: SessionWordSnapshot,
        words: List<SessionWordSnapshot>,
        result: TestResult,
    ): TransitionPlan {
        val wrong = result == TestResult.UNKNOWN
        val remaining = words.count {
            it.queueState == SessionWordQueueState.QUEUED && (it.lastAnsweredRound ?: -1) < session.roundNumber
        } - 1
        val otherWrong = words.any {
            it.id != current.id && it.queueState == SessionWordQueueState.QUEUED &&
                it.lastAnsweredRound == session.roundNumber
        }
        val hasWrong = wrong || otherWrong
        val finished = remaining == 0
        return TransitionPlan(
            phase = TestPhase.WRONG_REVIEW,
            status = if (finished && !hasWrong) SessionStatus.COMPLETED else SessionStatus.IN_PROGRESS,
            roundNumber = if (finished && hasWrong) session.roundNumber + 1 else session.roundNumber,
            currentState = if (wrong) SessionWordQueueState.QUEUED else SessionWordQueueState.PASSED,
            currentWasFirstWrong = current.wasFirstRoundWrong,
        )
    }
}

