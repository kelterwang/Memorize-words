package com.morningwords.data.local

import androidx.room.TypeConverter
import com.morningwords.domain.model.*

class Converters {
    @TypeConverter fun sessionType(value: SessionType) = value.name
    @TypeConverter fun sessionType(value: String) = SessionType.valueOf(value)
    @TypeConverter fun testMode(value: TestMode) = value.name
    @TypeConverter fun testMode(value: String) = TestMode.valueOf(value)
    @TypeConverter fun sessionStatus(value: SessionStatus) = value.name
    @TypeConverter fun sessionStatus(value: String) = SessionStatus.valueOf(value)
    @TypeConverter fun testPhase(value: TestPhase?) = value?.name
    @TypeConverter fun testPhase(value: String?) = value?.let(TestPhase::valueOf)
    @TypeConverter fun testResult(value: TestResult?) = value?.name
    @TypeConverter fun testResult(value: String?) = value?.let(TestResult::valueOf)
    @TypeConverter fun wrongStatus(value: WrongWordStatus) = value.name
    @TypeConverter fun wrongStatus(value: String) = WrongWordStatus.valueOf(value)
    @TypeConverter fun wrongRecordType(value: WrongRecordType) = value.name
    @TypeConverter fun wrongRecordType(value: String) = WrongRecordType.valueOf(value)
    @TypeConverter fun queueState(value: SessionWordQueueState) = value.name
    @TypeConverter fun queueState(value: String) = SessionWordQueueState.valueOf(value)
}

