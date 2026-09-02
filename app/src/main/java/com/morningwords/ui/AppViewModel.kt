package com.morningwords.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.morningwords.MorningWordsApplication
import com.morningwords.data.entity.WrongWordRow
import com.morningwords.data.repository.*
import com.morningwords.data.settings.AppSettings
import com.morningwords.domain.importer.ImportPreview
import com.morningwords.domain.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class AppUiState(
    val dashboard: Dashboard = Dashboard(0, 0, 0),
    val batches: List<BatchSummary> = emptyList(),
    val wrongWords: List<WrongWordRow> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val activeSessionId: Long? = null,
    val session: SessionView? = null,
    val feedbackCard: WordCard? = null,
    val importPreview: ImportPreview? = null,
    val selectedBatchIds: Set<Long> = emptySet(),
    val selectedMode: TestMode = TestMode.STUDENT,
    val answerVisible: Boolean = false,
    val isBusy: Boolean = false,
    val message: String? = null,
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as MorningWordsApplication
    private val repository = app.repository
    private val settingsRepository = app.settingsRepository
    private val mutable = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = mutable.asStateFlow()

    init {
        viewModelScope.launch {
            combine(repository.dashboard, repository.batches, repository.wrongWords, settingsRepository.settings) { dashboard, batches, wrong, settings ->
                Quad(dashboard, batches, wrong, settings)
            }.collect { data ->
                val availableBatchIds = data.b.mapTo(mutableSetOf()) { it.id }
                mutable.update {
                    it.copy(
                        dashboard = data.a,
                        batches = data.b,
                        wrongWords = data.c,
                        settings = data.d,
                        selectedBatchIds = it.selectedBatchIds intersect availableBatchIds,
                        selectedMode = if (it.selectedMode == TestMode.STUDENT) data.d.defaultTestMode else it.selectedMode,
                    )
                }
            }
        }
        refreshActive()
    }

    fun refreshActive() = viewModelScope.launch {
        mutable.update { it.copy(activeSessionId = repository.activeSessionId()) }
    }

    fun preview(text: String) { mutable.update { it.copy(importPreview = repository.previewImport(text), message = null) } }

    fun import(name: String, text: String, onDone: () -> Unit) = launchBusy {
        repository.importBatch(name, text)
        mutable.update { it.copy(importPreview = null, message = "批次已导入") }
        onDone()
    }

    fun toggleBatch(id: Long) = mutable.update {
        it.copy(selectedBatchIds = if (id in it.selectedBatchIds) it.selectedBatchIds - id else it.selectedBatchIds + id)
    }

    fun selectMode(mode: TestMode) { mutable.update { it.copy(selectedMode = mode) } }

    fun startDaily(onReady: (Long) -> Unit) = launchBusy {
        when (val result = repository.createDailySession(mutable.value.selectedBatchIds.toList(), mutable.value.selectedMode)) {
            is CreateSessionResult.Created -> onReady(result.sessionId)
            is CreateSessionResult.AlreadyInProgress -> onReady(result.sessionId)
            CreateSessionResult.Empty -> mutable.update { it.copy(message = "请至少选择一个有单词的批次") }
        }
        refreshActive()
    }

    fun startWrongReview(days: Int, onReady: (Long) -> Unit) = launchBusy {
        val end = System.currentTimeMillis()
        val start = end - days * 86_400_000L
        when (val result = repository.createWrongReview(start, end, mutable.value.selectedMode)) {
            is CreateSessionResult.Created -> onReady(result.sessionId)
            is CreateSessionResult.AlreadyInProgress -> onReady(result.sessionId)
            CreateSessionResult.Empty -> mutable.update { it.copy(message = "这个时间范围内没有待复测错词") }
        }
        refreshActive()
    }

    fun loadSession(id: Long) = viewModelScope.launch {
        mutable.update { it.copy(session = repository.loadSession(id), feedbackCard = null, answerVisible = false) }
    }

    fun revealAnswer() = mutable.update { it.copy(answerVisible = true) }

    fun answer(result: TestResult) {
        val sessionId = mutable.value.session?.session?.id ?: return
        if (mutable.value.isBusy) return
        launchBusy {
            val answeredCard = mutable.value.session?.current
            val next = repository.answer(sessionId, result)
            val showFeedback = shouldShowAnswerFeedback(
                result = result,
                showAnswerAfterKnow = mutable.value.settings.showAnswerAfterKnow,
                nextPhase = next.session.phase,
            )
            mutable.update { it.copy(session = next, feedbackCard = if (showFeedback) answeredCard else null, answerVisible = showFeedback) }
            if (showFeedback && result == TestResult.KNOW) {
                delay(700)
                mutable.update { it.copy(feedbackCard = null, answerVisible = false) }
            }
            refreshActive()
        }
    }

    fun retryWrongAnswers() {
        val sessionId = mutable.value.session?.session?.id ?: return
        launchBusy {
            mutable.update { it.copy(session = repository.retryWrongAnswers(sessionId), feedbackCard = null, answerVisible = false) }
        }
    }

    fun completeToday(onDone: () -> Unit) {
        val sessionId = mutable.value.session?.session?.id ?: return
        launchBusy {
            mutable.update { it.copy(session = repository.completeFromSummary(sessionId), feedbackCard = null, answerVisible = false, activeSessionId = null) }
            onDone()
        }
    }

    fun continueAfterAnswer() = viewModelScope.launch {
        mutable.value.session?.session?.id?.let { id -> mutable.update { it.copy(session = repository.loadSession(id), feedbackCard = null, answerVisible = false) } }
    }

    fun abandon(onDone: () -> Unit) = launchBusy {
        mutable.value.session?.session?.id?.let { repository.abandon(it) }
        mutable.update { it.copy(session = null, feedbackCard = null, activeSessionId = null) }
        onDone()
    }

    fun deleteBatch(id: Long) = launchBusy {
        val deleted = repository.deleteBatch(id)
        mutable.update { it.copy(message = if (deleted) "批次已删除，历史记录已保留" else "该批次正被晨测使用，暂不能删除") }
    }

    fun setMode(value: TestMode) = viewModelScope.launch { settingsRepository.setMode(value) }
    fun setAutoPronounce(value: Boolean) = viewModelScope.launch { settingsRepository.setAutoPronounce(value) }
    fun setShowAnswer(value: Boolean) = viewModelScope.launch { settingsRepository.setShowAnswer(value) }
    fun setLargeFont(value: Boolean) = viewModelScope.launch { settingsRepository.setLargeFont(value) }
    fun clearMessage() = mutable.update { it.copy(message = null) }
    fun showMessage(message: String) = mutable.update { it.copy(message = message) }

    private fun launchBusy(block: suspend () -> Unit) = viewModelScope.launch {
        mutable.update { it.copy(isBusy = true, message = null) }
        runCatching { block() }.onFailure { error -> mutable.update { it.copy(message = error.message ?: "操作失败，请重试") } }
        mutable.update { it.copy(isBusy = false) }
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

internal fun shouldShowAnswerFeedback(
    result: TestResult,
    showAnswerAfterKnow: Boolean,
    nextPhase: TestPhase,
): Boolean = result == TestResult.KNOW &&
    showAnswerAfterKnow &&
    nextPhase != TestPhase.ROUND_SUMMARY
