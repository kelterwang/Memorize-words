package com.morningwords.ui

import com.morningwords.domain.model.TestResult
import com.morningwords.domain.model.TestMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerFeedbackPolicyTest {
    @Test fun `student unknown answer advances immediately`() {
        assertFalse(shouldShowAnswerFeedback(TestMode.STUDENT, TestResult.UNKNOWN))
    }

    @Test fun `student known answer requires confirmation`() {
        assertTrue(shouldShowAnswerFeedback(TestMode.STUDENT, TestResult.KNOW))
    }

    @Test fun `student last known answer still requires confirmation before summary`() {
        assertTrue(shouldShowAnswerFeedback(TestMode.STUDENT, TestResult.KNOW))
    }

    @Test fun `parent known answer advances immediately`() {
        assertFalse(shouldShowAnswerFeedback(TestMode.PARENT, TestResult.KNOW))
    }

    @Test fun `parent unknown answer advances immediately`() {
        assertFalse(shouldShowAnswerFeedback(TestMode.PARENT, TestResult.UNKNOWN))
    }
}
