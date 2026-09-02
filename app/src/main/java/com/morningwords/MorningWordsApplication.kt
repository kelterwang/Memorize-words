package com.morningwords

import android.app.Application
import com.morningwords.data.local.AppDatabase
import com.morningwords.data.repository.MorningWordsRepository
import com.morningwords.data.settings.SettingsRepository

class MorningWordsApplication : Application() {
    val database by lazy { AppDatabase.create(this) }
    val repository by lazy { MorningWordsRepository(database) }
    val settingsRepository by lazy { SettingsRepository(this) }
}

