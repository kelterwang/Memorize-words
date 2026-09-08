package com.morningwords.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.morningwords.MorningWordsApplication
import com.morningwords.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class DefaultTestModeTest {
    @get:Rule val compose = createComposeRule()
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as MorningWordsApplication
    private val store = ViewModelStore()
    private lateinit var vm: AppViewModel
    private var batch = 0L
    private var previous = TestMode.STUDENT
    private var previousLargeFont = false

    @Before fun prepare() {
        runBlocking {
            previous = app.settingsRepository.settings.first().defaultTestMode
            previousLargeFont = app.settingsRepository.settings.first().largeFont
            app.database.clearAllTables()
            app.settingsRepository.setMode(TestMode.STUDENT)
            batch = app.repository.importBatch("默认方式验证", "apple n. 苹果")
        }
        compose.runOnIdle { vm = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(app))[AppViewModel::class.java] }
        compose.setContent { MorningWordsApp(vm) }
        compose.waitUntil(5_000) { vm.state.value.batches.size == 1 }
    }
    @After fun cleanup() {
        compose.runOnIdle { store.clear() }
        runBlocking {
            app.settingsRepository.setMode(previous)
            app.settingsRepository.setLargeFont(previousLargeFont)
        }
    }
    private fun scroll(text: String) { compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text)) }
    private fun chooseDefault(mode: TestMode) {
        compose.onNodeWithText("我的").performClick()
        compose.onNodeWithText(if (mode == TestMode.PARENT) "家长考我" else "学生自测").performClick()
        compose.waitUntil(5_000) { vm.state.value.settings.defaultTestMode == mode }
        compose.onNodeWithText("今日").performClick()
    }
    private fun startFromHome() {
        scroll("开始测试")
        compose.onNodeWithText("开始测试").performClick()
        if (batch !in vm.state.value.selectedBatchIds) compose.onNodeWithText("默认方式验证").performClick()
        scroll("开始")
        compose.onNodeWithText("开始").performClick()
        compose.waitUntil(5_000) { vm.state.value.session?.current != null }
    }
    @Test fun settingsDriveNewSessionInBothDirectionsAndKeepExistingSessionMode() {
        chooseDefault(TestMode.PARENT)
        startFromHome()
        assertEquals(TestMode.PARENT, vm.state.value.session!!.session.mode)
        compose.onNodeWithText("苹果").assertExists()
        val id = vm.state.value.session!!.session.id
        // Changing a default must never rewrite the mode of an existing session.
        compose.runOnIdle { vm.setMode(TestMode.STUDENT) }
        compose.waitUntil(5_000) { vm.state.value.settings.defaultTestMode == TestMode.STUDENT }
        assertEquals(TestMode.PARENT, runBlocking { app.repository.loadSession(id)!!.session.mode })
        compose.onNodeWithContentDescription("放弃").performClick()
        compose.onNodeWithText("放弃测试").performClick()
        compose.waitUntil(5_000) { vm.state.value.session == null && !vm.state.value.isBusy }
        chooseDefault(TestMode.STUDENT)
        startFromHome()
        assertEquals(TestMode.STUDENT, vm.state.value.session!!.session.mode)
        compose.onNodeWithText("苹果").assertDoesNotExist()
    }
    @Test fun temporaryChoiceSurvivesUpdatesAndNextSetupUsesDefaultForWrongReview() {
        chooseDefault(TestMode.PARENT)
        scroll("开始测试")
        compose.onNodeWithText("开始测试").performClick()
        scroll("学生自测")
        compose.onNodeWithText("学生自测").performClick()
        compose.runOnIdle { vm.setLargeFont(!vm.state.value.settings.largeFont) }
        compose.waitForIdle()
        // A repository emission must not turn a deliberate STUDENT override into PARENT.
        runBlocking { app.repository.renameBatch(batch, "更新后的批次") }
        compose.waitUntil(5_000) { vm.state.value.batches.single().name == "更新后的批次" }
        assertEquals(TestMode.STUDENT, vm.state.value.selectedMode)
        compose.onNodeWithContentDescription("返回").performClick()
        scroll("开始测试")
        compose.onNodeWithText("开始测试").performClick()
        compose.waitUntil(5_000) { vm.state.value.selectedMode == TestMode.PARENT }
        compose.runOnIdle { vm.toggleBatch(batch) }
        scroll("开始")
        compose.onNodeWithText("开始").performClick()
        compose.waitUntil(5_000) { vm.state.value.session?.current != null }
        compose.onNodeWithText("不会", useUnmergedTree = true).performClick()
        compose.waitUntil(5_000) { vm.state.value.session?.session?.phase == TestPhase.ROUND_SUMMARY }
        scroll("退出并完成今日测试")
        compose.onNodeWithText("退出并完成今日测试").performClick()
        compose.waitUntil(5_000) { vm.state.value.activeSessionId == null }
        chooseDefault(TestMode.STUDENT)
        compose.onNodeWithText("错词").performClick()
        compose.onNodeWithText("开始复测").performClick()
        compose.waitUntil(5_000) { vm.state.value.selectedMode == TestMode.STUDENT }
        scroll("开始复测")
        compose.onNodeWithText("开始复测").performClick()
        compose.waitUntil(5_000) { vm.state.value.session?.session?.type == SessionType.WRONG_REVIEW }
        assertEquals(TestMode.STUDENT, vm.state.value.session!!.session.mode)
        compose.onNodeWithText("苹果").assertDoesNotExist()
    }
}
