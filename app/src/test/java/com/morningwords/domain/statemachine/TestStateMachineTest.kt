package com.morningwords.domain.statemachine

import com.morningwords.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TestStateMachineTest {
    private val machine = TestStateMachine()

    @Test fun `zero-error first round completes`() {
        val word = word(id = 1)
        val plan = machine.transition(session(total = 1), word, listOf(word), TestResult.KNOW)
        assertEquals(SessionStatus.COMPLETED, plan.status)
        assertEquals(TestPhase.COMPLETED, plan.phase)
        assertEquals(1, plan.firstPassDelta)
    }

    @Test fun `first-round error enters wrong loop and is immutable final set`() {
        val word = word(id = 1)
        val plan = machine.transition(session(total = 1), word, listOf(word), TestResult.UNKNOWN)
        assertEquals(TestPhase.WRONG_LOOP, plan.phase)
        assertEquals(1, plan.firstWrongDelta)
        assertTrue(plan.currentWasFirstWrong)
        assertEquals(SessionWordQueueState.QUEUED, plan.currentState)
    }

    @Test fun `cleared wrong loop rebuilds full final check`() {
        val current = word(id = 1, firstWrong = true, state = SessionWordQueueState.QUEUED, lastRound = 0)
        val plan = machine.transition(
            session(total = 1, phase = TestPhase.WRONG_LOOP, round = 1, wrong = 1),
            current, listOf(current), TestResult.KNOW,
        )
        assertEquals(TestPhase.FINAL_CHECK, plan.phase)
        assertTrue(plan.resetFinalCheck)
    }

    @Test fun `failed final check returns only failures to wrong loop`() {
        val current = word(id = 1, firstWrong = true, state = SessionWordQueueState.QUEUED, lastRound = null)
        val plan = machine.transition(
            session(total = 1, phase = TestPhase.FINAL_CHECK, round = 2, wrong = 1),
            current, listOf(current), TestResult.UNKNOWN,
        )
        assertEquals(TestPhase.WRONG_LOOP, plan.phase)
        assertEquals(SessionWordQueueState.QUEUED, plan.currentState)
        assertEquals(3, plan.roundNumber)
    }

    @Test fun `successful full final check completes`() {
        val current = word(id = 1, firstWrong = true, state = SessionWordQueueState.QUEUED)
        val plan = machine.transition(
            session(total = 1, phase = TestPhase.FINAL_CHECK, round = 2, wrong = 1),
            current, listOf(current), TestResult.KNOW,
        )
        assertEquals(SessionStatus.COMPLETED, plan.status)
    }

    @Test fun `wrong review loops unknown without final check`() {
        val current = word(id = 1, state = SessionWordQueueState.QUEUED)
        val plan = machine.transition(
            session(total = 1, type = SessionType.WRONG_REVIEW, phase = TestPhase.WRONG_REVIEW, round = 1),
            current, listOf(current), TestResult.UNKNOWN,
        )
        assertEquals(TestPhase.WRONG_REVIEW, plan.phase)
        assertEquals(SessionStatus.IN_PROGRESS, plan.status)
        assertEquals(2, plan.roundNumber)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `daily test rejects mastered`() {
        val current = word(id = 1)
        machine.transition(session(total = 1), current, listOf(current), TestResult.MASTERED)
    }

    private fun session(
        total: Int,
        type: SessionType = SessionType.DAILY_TEST,
        phase: TestPhase = TestPhase.FIRST_ROUND,
        round: Int = 0,
        wrong: Int = 0,
    ) = SessionSnapshot(1, type, TestMode.STUDENT, SessionStatus.IN_PROGRESS, phase, total, 0, wrong, round)

    private fun word(
        id: Long,
        firstWrong: Boolean = false,
        state: SessionWordQueueState = SessionWordQueueState.NOT_ANSWERED,
        lastRound: Int? = null,
    ) = SessionWordSnapshot(id, id, id.toInt(), firstWrong, state, id.toInt(), lastRound, null)
}

