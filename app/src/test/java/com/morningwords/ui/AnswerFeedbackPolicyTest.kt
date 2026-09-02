package com.morningwords.ui

import com.morningwords.domain.model.TestPhase
import com.morningwords.domain.model.TestResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerFeedbackPolicyTest {
    @Test fun `unknown answer advances immediately`() {
        assertFalse(shouldShowAnswerFeedback(TestResult.UNKNOWN, true, TestPhase.FIRST_ROUND))
    }

    @Test fun `known answer can briefly show feedback when enabled`() {
        assertTrue(shouldShowAnswerFeedback(TestResult.KNOW, true, TestPhase.FIRST_ROUND))
        assertFalse(shouldShowAnswerFeedback(TestResult.KNOW, false, TestPhase.FIRST_ROUND))
    }

    @Test fun `last answer opens round summary without feedback`() {
        assertFalse(shouldShowAnswerFeedback(TestResult.KNOW, true, TestPhase.ROUND_SUMMARY))
        assertFalse(shouldShowAnswerFeedback(TestResult.UNKNOWN, true, TestPhase.ROUND_SUMMARY))
    }
}
