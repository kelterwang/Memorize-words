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
            TestPhase.WRONG_LOOP, TestPhase.FINAL_CHECK -> retryRound(session, current, allWords, result)
            TestPhase.WRONG_REVIEW -> retryRound(session, current, allWords, result)
            TestPhase.ROUND_SUMMARY -> error("Summary cannot be answered")
            TestPhase.COMPLETED -> error("Completed session cannot be answered")
        }
    }

    private fun firstRound(
        session: SessionSnapshot,
        current: SessionWordSnapshot,
        words: List<SessionWordSnapshot>,
        result: TestResult,
    ): TransitionPlan {
        val wrong = result == TestResult.UNKNOWN
        val remaining = words.count { it.queueState == SessionWordQueueState.NOT_ANSWERED } - 1
        val finished = remaining == 0
        return TransitionPlan(
            phase = when {
                !finished -> TestPhase.FIRST_ROUND
                else -> TestPhase.ROUND_SUMMARY
            },
            status = SessionStatus.IN_PROGRESS,
            roundNumber = session.roundNumber,
            firstPassDelta = if (wrong) 0 else 1,
            firstWrongDelta = if (wrong) 1 else 0,
            currentState = if (wrong) SessionWordQueueState.QUEUED else SessionWordQueueState.PASSED,
            currentWasFirstWrong = wrong || current.wasFirstRoundWrong,
        )
    }

    private fun retryRound(
        session: SessionSnapshot,
        current: SessionWordSnapshot,
        words: List<SessionWordSnapshot>,
        result: TestResult,
    ): TransitionPlan {
        val wrong = result == TestResult.UNKNOWN
        val remainingThisRound = words.count {
            it.queueState == SessionWordQueueState.QUEUED && (it.lastAnsweredRound ?: -1) < session.roundNumber
        } - 1
        val roundFinished = remainingThisRound == 0
        return TransitionPlan(
            phase = if (roundFinished) TestPhase.ROUND_SUMMARY else session.phase,
            status = SessionStatus.IN_PROGRESS,
            roundNumber = session.roundNumber,
            currentState = if (wrong) SessionWordQueueState.QUEUED else SessionWordQueueState.PASSED,
            currentWasFirstWrong = current.wasFirstRoundWrong,
        )
    }
}
