package com.morningwords.ui

import com.morningwords.domain.model.TestResult
import com.morningwords.domain.model.TestMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerFeedbackPolicyTest {
    @Test fun `student unknown answer advances immediately`() {
        assertFalse(needsStudentAnswerConfirmation(TestMode.STUDENT, TestResult.UNKNOWN))
    }

    @Test fun `student known answer requires self assessment`() {
        assertTrue(needsStudentAnswerConfirmation(TestMode.STUDENT, TestResult.KNOW))
    }

    @Test fun `student last known answer still requires self assessment before summary`() {
        assertTrue(needsStudentAnswerConfirmation(TestMode.STUDENT, TestResult.KNOW))
    }

    @Test fun `parent known answer advances immediately`() {
        assertFalse(needsStudentAnswerConfirmation(TestMode.PARENT, TestResult.KNOW))
    }

    @Test fun `parent unknown answer advances immediately`() {
        assertFalse(needsStudentAnswerConfirmation(TestMode.PARENT, TestResult.UNKNOWN))
    }
}
