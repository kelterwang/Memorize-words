package com.morningwords.ui

import android.app.Application
import android.net.Uri
import com.morningwords.speech.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.morningwords.MorningWordsApplication
import com.morningwords.data.entity.WordEntity
import com.morningwords.data.entity.WrongWordRow
import com.morningwords.data.repository.*
import com.morningwords.data.settings.AppSettings
import com.morningwords.domain.importer.ImportPreview
import com.morningwords.domain.model.*
import com.morningwords.domain.motivation.DAILY_QUOTES
import com.morningwords.domain.motivation.greetingForHour
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalTime

data class AppUiState(
    val dashboard: Dashboard = Dashboard(0, 0, 0),
    val greeting: String = greetingForHour(LocalTime.now().hour),
    val dailyQuote: String = DAILY_QUOTES.first(),
    val batches: List<BatchSummary> = emptyList(),
    val batchDetailId: Long? = null,
    val batchDetailWords: List<WordEntity> = emptyList(),
    val isBatchDetailLoading: Boolean = false,
    val wrongWords: List<WrongWordRow> = emptyList(),
    val wrongWordGroups: List<WrongWordGroup> = emptyList(),
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
    val voicePackInstalled: Boolean = false,
    val voicePackImporting: Boolean = false,
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as MorningWordsApplication
    private val repository = app.repository
    private val settingsRepository = app.settingsRepository
    private val mutable = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = mutable.asStateFlow()

    init {
        viewModelScope.launch { repository.repairImportedWordFields() }
        refreshVoicePack()
        refreshHomeMessage()
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
        viewModelScope.launch {
            repository.wrongWordGroups.collect { groups ->
                mutable.update { it.copy(wrongWordGroups = groups) }
            }
        }
        refreshActive()
    }

    fun refreshActive() = viewModelScope.launch {
        mutable.update { it.copy(activeSessionId = repository.activeSessionId()) }
    }

    fun refreshHomeMessage() {
        mutable.update { it.copy(greeting = greetingForHour(LocalTime.now().hour)) }
        viewModelScope.launch {
            val quote = settingsRepository.dailyQuote()
            mutable.update { it.copy(dailyQuote = quote) }
        }
    }

    fun preview(text: String) { mutable.update { it.copy(importPreview = repository.previewImport(text), message = null) } }

    fun loadBatchDetail(batchId: Long) = viewModelScope.launch {
        mutable.update { it.copy(batchDetailId = batchId, batchDetailWords = emptyList(), isBatchDetailLoading = true) }
        runCatching { repository.wordsInBatch(batchId) }
            .onSuccess { words ->
                mutable.update { current ->
                    if (current.batchDetailId == batchId) current.copy(batchDetailWords = words, isBatchDetailLoading = false) else current
                }
            }
            .onFailure { error ->
                mutable.update { current ->
                    current.copy(isBatchDetailLoading = false, message = error.message ?: "词库加载失败")
                }
            }
    }

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

    fun startWrongReview(batchIds: Set<Long>, onReady: (Long) -> Unit) = launchBusy {
        when (val result = repository.createWrongReview(mutable.value.selectedMode, batchIds.toList())) {
            is CreateSessionResult.Created -> onReady(result.sessionId)
            is CreateSessionResult.AlreadyInProgress -> onReady(result.sessionId)
            CreateSessionResult.Empty -> mutable.update { it.copy(message = "所选文件夹内没有待复测错词") }
        }
        refreshActive()
    }

    fun loadSession(id: Long) = viewModelScope.launch {
        mutable.update { it.copy(session = repository.loadSession(id), feedbackCard = null, answerVisible = false) }
    }

    fun revealAnswer() = mutable.update { it.copy(answerVisible = true) }

    fun answer(result: TestResult) {
        val current = mutable.value
        val session = current.session ?: return
        if (current.isBusy) return
        if (result == TestResult.MASTERED && session.session.type != SessionType.WRONG_REVIEW) return
        if (needsStudentAnswerConfirmation(session.session.mode, result) && !current.answerVisible) {
            mutable.update { it.copy(feedbackCard = session.current, answerVisible = true) }
            return
        }
        submitAnswer(result)
    }

    fun confirmSelfAssessment(isCorrect: Boolean) {
        val current = mutable.value
        if (current.isBusy || !current.answerVisible || current.session?.session?.mode != TestMode.STUDENT) return
        submitAnswer(if (isCorrect) TestResult.KNOW else TestResult.UNKNOWN)
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

    fun abandon(onDone: () -> Unit) = launchBusy {
        mutable.value.session?.session?.id?.let { repository.abandon(it) }
        mutable.update { it.copy(session = null, feedbackCard = null, activeSessionId = null) }
        onDone()
    }

    fun deleteBatch(id: Long) = launchBusy {
        val deleted = repository.deleteBatch(id)
        mutable.update { it.copy(message = if (deleted) "批次已删除，历史记录已保留" else "该批次正被晨测使用，暂不能删除") }
    }

    fun renameBatch(id: Long, name: String) = launchBusy {
        repository.renameBatch(id, name)
        mutable.update { it.copy(message = "词库名称已更新") }
    }

    fun setMode(value: TestMode) = viewModelScope.launch { settingsRepository.setMode(value) }
    fun setAutoPronounce(value: Boolean) = viewModelScope.launch { settingsRepository.setAutoPronounce(value) }
    fun setShowAnswer(value: Boolean) = viewModelScope.launch { settingsRepository.setShowAnswer(value) }
    fun setSpeechSource(value: SpeechSource) = viewModelScope.launch { settingsRepository.setSpeechSource(value) }
    fun setEnglishAccent(value: EnglishAccent) = viewModelScope.launch { settingsRepository.setEnglishAccent(value) }
    fun refreshVoicePack() { mutable.update { it.copy(voicePackInstalled = KokoroPack(app).installed) } }
    fun importVoicePack(uri: Uri) {
        if (mutable.value.voicePackImporting) return
        mutable.update { it.copy(voicePackImporting = true) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val input = app.contentResolver.openInputStream(uri) ?: error("无法读取语音包")
                    input.use { KokoroPack(app).install(it) }
                }
                refreshVoicePack()
                settingsRepository.setSpeechSource(SpeechSource.KOKORO)
                showMessage("Kokoro 语音包已导入，可离线使用")
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                showMessage(e.message ?: "导入失败，请检查文件和剩余空间")
            } finally { mutable.update { it.copy(voicePackImporting = false) } }
        }
    }

    fun setLargeFont(value: Boolean) = viewModelScope.launch { settingsRepository.setLargeFont(value) }
    fun clearMessage() = mutable.update { it.copy(message = null) }
    fun showMessage(message: String) = mutable.update { it.copy(message = message) }

    private fun submitAnswer(result: TestResult) {
        val sessionId = mutable.value.session?.session?.id ?: return
        if (mutable.value.isBusy) return
        mutable.update { it.copy(isBusy = true, message = null) }
        viewModelScope.launch {
            runCatching { repository.answer(sessionId, result) }
                .onSuccess { next ->
                    mutable.update { it.copy(session = next, feedbackCard = null, answerVisible = false) }
                    refreshActive()
                }
                .onFailure { error -> mutable.update { it.copy(message = error.message ?: "操作失败，请重试") } }
            mutable.update { it.copy(isBusy = false) }
        }
    }

    private fun launchBusy(block: suspend () -> Unit) = viewModelScope.launch {
        mutable.update { it.copy(isBusy = true, message = null) }
        runCatching { block() }.onFailure { error -> mutable.update { it.copy(message = error.message ?: "操作失败，请重试") } }
        mutable.update { it.copy(isBusy = false) }
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

internal fun needsStudentAnswerConfirmation(mode: TestMode, result: TestResult): Boolean =
    mode == TestMode.STUDENT && (result == TestResult.KNOW || result == TestResult.MASTERED)
