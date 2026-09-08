package com.morningwords.data.settings

import android.content.Context
import com.morningwords.speech.SpeechSource
import com.morningwords.speech.EnglishAccent
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.morningwords.domain.model.TestMode
import com.morningwords.domain.motivation.DAILY_QUOTES
import com.morningwords.domain.motivation.selectDailyQuote
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import kotlin.random.Random

private val Context.dataStore by preferencesDataStore("settings")

data class AppSettings(
    val defaultTestMode: TestMode = TestMode.STUDENT,
    val autoPronounce: Boolean = true,
    val showAnswerAfterKnow: Boolean = true,
    val largeFont: Boolean = false,
    val speechSource: SpeechSource = SpeechSource.SYSTEM,
    val englishAccent: EnglishAccent = EnglishAccent.US,
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val speechSource = stringPreferencesKey("speechSource")
        val englishAccent = stringPreferencesKey("englishAccent")
        val mode = stringPreferencesKey("defaultTestMode")
        val autoPronounce = booleanPreferencesKey("autoPronounce")
        val showAnswer = booleanPreferencesKey("showAnswerAfterKnow")
        val largeFont = booleanPreferencesKey("largeFont")
        val quoteDate = stringPreferencesKey("dailyQuoteDate")
        val quoteIndex = intPreferencesKey("dailyQuoteIndex")
        val quoteRemaining = stringPreferencesKey("dailyQuoteRemaining")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { values ->
        AppSettings(
            defaultTestMode = runCatching { TestMode.valueOf(values[Keys.mode] ?: "STUDENT") }.getOrDefault(TestMode.STUDENT),
            autoPronounce = values[Keys.autoPronounce] ?: true,
            showAnswerAfterKnow = values[Keys.showAnswer] ?: true,
            largeFont = values[Keys.largeFont] ?: false,
            speechSource = runCatching { SpeechSource.valueOf(values[Keys.speechSource] ?: "SYSTEM") }.getOrDefault(SpeechSource.SYSTEM),
            englishAccent = runCatching { EnglishAccent.valueOf(values[Keys.englishAccent] ?: "US") }.getOrDefault(EnglishAccent.US),
        )
    }

    suspend fun setSpeechSource(value: SpeechSource) = context.dataStore.edit { it[Keys.speechSource] = value.name }
    suspend fun setEnglishAccent(value: EnglishAccent) = context.dataStore.edit { it[Keys.englishAccent] = value.name }

    suspend fun setMode(value: TestMode) = context.dataStore.edit { it[Keys.mode] = value.name }
    suspend fun setAutoPronounce(value: Boolean) = context.dataStore.edit { it[Keys.autoPronounce] = value }
    suspend fun setShowAnswer(value: Boolean) = context.dataStore.edit { it[Keys.showAnswer] = value }
    suspend fun setLargeFont(value: Boolean) = context.dataStore.edit { it[Keys.largeFont] = value }

    suspend fun dailyQuote(today: String = LocalDate.now().toString()): String {
        var selectedQuote = DAILY_QUOTES.first()
        context.dataStore.edit { values ->
            val remaining = values[Keys.quoteRemaining]
                ?.split(',')
                ?.mapNotNull(String::toIntOrNull)
                ?.toSet()
                .orEmpty()
            val selection = selectDailyQuote(
                today = today,
                previousDate = values[Keys.quoteDate],
                previousIndex = values[Keys.quoteIndex],
                remainingIndices = remaining,
                randomValue = Random.nextInt(),
            )
            selectedQuote = DAILY_QUOTES[selection.quoteIndex]
            if (selection.changed) {
                values[Keys.quoteDate] = today
                values[Keys.quoteIndex] = selection.quoteIndex
                values[Keys.quoteRemaining] = selection.remainingIndices.sorted().joinToString(",")
            }
        }
        return selectedQuote
    }
}
