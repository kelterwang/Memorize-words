package com.morningwords.ui

import com.morningwords.domain.model.TestResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerFeedbackPolicyTest {
    @Test fun `unknown answer advances immediately`() {
        assertFalse(shouldShowAnswerFeedback(TestResult.UNKNOWN))
    }

    @Test fun `known answer always requires confirmation`() {
        assertTrue(shouldShowAnswerFeedback(TestResult.KNOW))
    }

    @Test fun `last known answer still requires confirmation before summary`() {
        assertTrue(shouldShowAnswerFeedback(TestResult.KNOW))
        assertFalse(shouldShowAnswerFeedback(TestResult.UNKNOWN))
    }
}
