package com.morningwords.speech

import java.util.Locale

enum class SpeechSource { SYSTEM, KOKORO }
enum class EnglishAccent(val locale: Locale, val kokoroLanguage: String, val speakerId: Int) {
    US(Locale.US, "en-us", 3),
    UK(Locale.UK, "en", 21),
}

const val SPEECH_RATE = 1.0f
