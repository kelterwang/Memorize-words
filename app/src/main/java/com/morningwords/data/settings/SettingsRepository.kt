package com.morningwords.data.settings

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.morningwords.domain.model.TestMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

data class AppSettings(
    val defaultTestMode: TestMode = TestMode.STUDENT,
    val autoPronounce: Boolean = true,
    val showAnswerAfterKnow: Boolean = true,
    val largeFont: Boolean = false,
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val mode = stringPreferencesKey("defaultTestMode")
        val autoPronounce = booleanPreferencesKey("autoPronounce")
        val showAnswer = booleanPreferencesKey("showAnswerAfterKnow")
        val largeFont = booleanPreferencesKey("largeFont")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { values ->
        AppSettings(
            defaultTestMode = runCatching { TestMode.valueOf(values[Keys.mode] ?: "STUDENT") }.getOrDefault(TestMode.STUDENT),
            autoPronounce = values[Keys.autoPronounce] ?: true,
            showAnswerAfterKnow = values[Keys.showAnswer] ?: true,
            largeFont = values[Keys.largeFont] ?: false,
        )
    }

    suspend fun setMode(value: TestMode) = context.dataStore.edit { it[Keys.mode] = value.name }
    suspend fun setAutoPronounce(value: Boolean) = context.dataStore.edit { it[Keys.autoPronounce] = value }
    suspend fun setShowAnswer(value: Boolean) = context.dataStore.edit { it[Keys.showAnswer] = value }
    suspend fun setLargeFont(value: Boolean) = context.dataStore.edit { it[Keys.largeFont] = value }
}

