package com.morningwords.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.navigation.compose.rememberNavController
import androidx.test.platform.app.InstrumentationRegistry
import com.morningwords.MorningWordsApplication
import com.morningwords.data.repository.CreateSessionResult
import com.morningwords.domain.model.TestMode
import com.morningwords.domain.model.TestResult
import com.morningwords.domain.model.WrongWordStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class StudentReviewConfirmationTest {
    @get:Rule val compose = createComposeRule()
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as MorningWordsApplication
    private val store = ViewModelStore()
    private lateinit var vm: AppViewModel
    private var wordId = 0L

    @Before fun prepareIsolatedTestDatabase() = runBlocking {
        // Run instrumentation only on the dedicated test emulator, never the learning device.
        app.database.clearAllTables()
        val batch = app.repository.importBatch("确认掌握测试", "apple n. 苹果 This is an apple.")
        wordId = app.repository.wordsInBatch(batch).single().id
        val session = (app.repository.createDailySession(listOf(batch), TestMode.STUDENT) as CreateSessionResult.Created).sessionId
        app.repository.answer(session, TestResult.UNKNOWN)
        app.repository.completeFromSummary(session)
        Unit
    }

    @After fun disposeViewModel() {
        compose.runOnIdle { store.clear() }
    }

    private fun openReview(mode: TestMode = TestMode.STUDENT) {
        val sessionId = runBlocking {
            val batches = app.database.dao().getBatches().map { it.id }
            (app.repository.createWrongReview(mode, batches) as CreateSessionResult.Created).sessionId
        }
        compose.runOnIdle {
            vm = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(app))[AppViewModel::class.java]
            vm.loadSession(sessionId)
        }
        compose.setContent {
            val state by vm.state.collectAsState()
            MaterialTheme { TestScreen(state, vm, rememberNavController()) }
        }
        compose.waitUntil(5_000) { vm.state.value.session != null }
    }

    private fun wrong() = runBlocking { checkNotNull(app.database.dao().wrongWord(wordId)) }

    @Test fun studentCanRemoveWordOnlyAfterOpeningTheExplanation() {
        openReview()
        compose.onNodeWithText("苹果").assertDoesNotExist()
        compose.onNodeWithText("会（移出错词库）").assertDoesNotExist()
        compose.onNodeWithText("会", useUnmergedTree = true).performClick()
        compose.onNodeWithText("苹果").assertIsDisplayed()
        compose.onNodeWithText("我错了").assertIsDisplayed()
        compose.onNodeWithText("我对了").assertIsDisplayed()
        assertEquals(WrongWordStatus.ACTIVE, wrong().status)
        compose.onNodeWithText("会（移出错词库）").assertIsDisplayed().performClick()
        compose.waitUntil(5_000) { wrong().status == WrongWordStatus.MASTERED }
        assertEquals(1, wrong().wrongCount)
    }

    @Test fun correctingSelfAssessmentKeepsWordAndRecordsReviewError() {
        openReview()
        compose.onNodeWithText("会", useUnmergedTree = true).performClick()
        compose.onNodeWithText("苹果").assertIsDisplayed()
        compose.onNodeWithText("我错了").performClick()
        compose.waitUntil(5_000) { wrong().reviewWrongCount == 1 }
        assertEquals(WrongWordStatus.ACTIVE, wrong().status)
    }

    @Test fun correctAnswerWithoutExplicitRemovalKeepsWordForLaterReview() {
        openReview()
        compose.onNodeWithText("会", useUnmergedTree = true).performClick()
        compose.onNodeWithText("我对了").performClick()
        compose.waitUntil(5_000) { vm.state.value.session?.roundTestedCount == 1 }
        assertEquals(WrongWordStatus.ACTIVE, wrong().status)
        assertEquals(0, wrong().reviewWrongCount)
    }

    @Test fun directMasteryRequestCannotBypassStudentExplanation() {
        openReview()
        compose.runOnIdle { vm.answer(TestResult.MASTERED) }
        compose.onNodeWithText("苹果").assertIsDisplayed()
        assertEquals(WrongWordStatus.ACTIVE, wrong().status)
        assertEquals(0, vm.state.value.session?.roundTestedCount)
        compose.onNodeWithText("会（移出错词库）").assertIsDisplayed()
    }

    @Test fun parentCanRemoveWordFromAlreadyVisibleExplanation() {
        openReview(TestMode.PARENT)
        compose.onNodeWithText("苹果").assertIsDisplayed()
        compose.onNodeWithText("会（移出错词库）").performClick()
        compose.waitUntil(5_000) { wrong().status == WrongWordStatus.MASTERED }
    }
}
