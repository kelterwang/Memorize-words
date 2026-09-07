package com.morningwords.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.morningwords.MorningWordsApplication
import com.morningwords.data.repository.SessionView
import com.morningwords.domain.model.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class StitchScreensTest {
    @get:Rule val compose = createComposeRule()
    private val store = ViewModelStore()
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as MorningWordsApplication

    @After fun cleanup() { compose.runOnIdle { store.clear() } }

    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        val file = File(app.getExternalFilesDir(null), name)
        file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun librarySearchAndDeleteConfirmationUseRealData() {
        runBlocking {
            app.database.clearAllTables()
            app.repository.importBatch("高考核心高频词", "subordinate adj. 次要的 It was subordinate to images.")
            app.repository.importBatch("高二必修三", "achieve v. 达到")
            app.repository.importBatch("易混淆形近词专项", "maintain v. 保持")
        }
        lateinit var vm: AppViewModel
        compose.runOnIdle { vm = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(app))[AppViewModel::class.java] }
        compose.setContent { MorningWordsApp(vm) }
        compose.waitUntil(5_000) { vm.state.value.batches.size == 3 }
        capture("stitch-home.png")
        compose.onNodeWithText("词库").performClick()
        compose.onNodeWithText("我的词库").assertIsDisplayed()
        capture("stitch-library.png")
        compose.onNodeWithText("搜索批次名称").performTextInput("不存在")
        compose.onNodeWithText("没有匹配的词库").assertExists()
        compose.onNodeWithContentDescription("清空搜索").performClick()
        compose.onNodeWithText("搜索批次名称").performTextInput("高考")
        compose.onNodeWithContentDescription("删除").performScrollTo().performClick()
        compose.onNodeWithText("删除词库？").assertIsDisplayed()
        compose.onNodeWithText("取消").performClick()
        assertEquals(3, vm.state.value.batches.size)
    }

    @Test fun studentScreensPreserveConfirmationAndCanCancelAbandon() {
        runBlocking {
            app.database.clearAllTables()
            app.repository.importBatch("高考核心词汇", "subordinate adj. 次要的 It was subordinate to images.")
        }
        lateinit var vm: AppViewModel
        compose.runOnIdle { vm = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(app))[AppViewModel::class.java] }
        compose.setContent { MorningWordsApp(vm) }
        compose.waitUntil(5_000) { vm.state.value.batches.size == 1 }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("开始测试"))
        compose.onNodeWithText("开始测试").performClick()
        compose.onNodeWithText("高考核心词汇").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("开始"))
        compose.onNodeWithText("开始").performClick()
        compose.waitUntil(5_000) { vm.state.value.session?.current != null }
        compose.onNodeWithText("次要的").assertDoesNotExist()
        capture("stitch-student-hidden.png")
        compose.onNodeWithText("会", useUnmergedTree = true).performClick()
        compose.onNodeWithText("次要的").assertIsDisplayed()
        assertEquals(0, vm.state.value.session!!.roundTestedCount)
        capture("stitch-student-revealed.png")
        compose.onNodeWithContentDescription("放弃").performClick()
        compose.onNodeWithText("放弃本次测试？").assertIsDisplayed()
        compose.onNodeWithText("继续作答").performClick()
        compose.onNodeWithText("我错了").performClick()
        compose.waitUntil(5_000) { vm.state.value.session?.roundWrongCount == 1 }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("本轮待攻克单词"))
        compose.onNodeWithText("subordinate").assertExists()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        capture("stitch-summary-with-errors.png")
    }

    @Test fun summaryActionsRemainReachableWithLargeFontOnSmallScreen() {
        var retried = false
        var completed = false
        val summary = SessionView(SessionSnapshot(1, SessionType.DAILY_TEST, TestMode.STUDENT,
            SessionStatus.IN_PROGRESS, TestPhase.ROUND_SUMMARY, 4, 3, 1, 0), null, 3, 1, 4, 4, 3, 1, listOf("subordinate"))
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                MaterialTheme {
                    Box(Modifier.width(320.dp).height(500.dp)) {
                        RoundSummaryContent(summary, false, { retried = true }, { completed = true })
                    }
                }
            }
        }
        compose.onNodeWithText("本轮正确率 75%").assertExists()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("subordinate"))
        compose.onNodeWithText("subordinate").assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("错题重新测试（1）"))
        compose.onNodeWithText("错题重新测试（1）").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("退出并完成今日测试"))
        compose.onNodeWithText("退出并完成今日测试").performClick()
        compose.runOnIdle { assertTrue(retried); assertTrue(completed) }
    }

    @Test fun summaryShowsRealCountsAndNoRetryForPerfectRound() {
        val summary = SessionView(SessionSnapshot(1, SessionType.DAILY_TEST, TestMode.STUDENT,
            SessionStatus.IN_PROGRESS, TestPhase.ROUND_SUMMARY, 4, 4, 0, 0), null, 4, 0, 4, 4, 4, 0)
        compose.setContent { MaterialTheme { RoundSummaryContent(summary, false, {}, {}) } }
        compose.onNodeWithText("本轮正确率 100%").assertExists()
        capture("stitch-summary.png")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("本轮没有错题"))
        compose.onNodeWithText("本轮没有错题").assertIsNotEnabled()
        compose.onNodeWithText("退出并完成今日测试").assertIsEnabled()
        compose.onNodeWithText("本轮待攻克单词").assertDoesNotExist()
    }
}
