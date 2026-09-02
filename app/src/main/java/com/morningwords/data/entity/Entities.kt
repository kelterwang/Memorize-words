package com.morningwords.data.entity

import androidx.room.*
import com.morningwords.domain.model.*

@Entity(tableName = "Word", indices = [Index(value = ["normalizedWord"], unique = true)])
data class WordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val word: String,
    val normalizedWord: String,
    val phonetic: String? = null,
    val partOfSpeech: String? = null,
    val meaning: String? = null,
    val example: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "WordBatch", indices = [Index("createdAt")])
data class WordBatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val batchName: String,
    val createdAt: Long,
    val studyDate: Long? = null,
    val source: String? = null,
    val remark: String? = null,
)

@Entity(
    tableName = "BatchWord",
    foreignKeys = [
        ForeignKey(entity = WordBatchEntity::class, parentColumns = ["id"], childColumns = ["batchId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = WordEntity::class, parentColumns = ["id"], childColumns = ["wordId"]),
    ],
    indices = [Index(value = ["batchId", "wordId"], unique = true), Index(value = ["batchId", "sortOrder"]), Index("wordId")],
)
data class BatchWordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val batchId: Long,
    val wordId: Long,
    val requiredMeaning: String?,
    val sortOrder: Int,
    val rawText: String?,
)

@Entity(tableName = "TestSession", indices = [Index("status"), Index("updatedAt")])
data class TestSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionType: SessionType,
    val testMode: TestMode,
    val status: SessionStatus,
    val phase: TestPhase,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long? = null,
    val totalCount: Int,
    val firstPassCount: Int = 0,
    val firstWrongCount: Int = 0,
    val currentIndex: Int = 0,
    val roundNumber: Int = 0,
)

@Entity(
    tableName = "SessionBatch",
    foreignKeys = [ForeignKey(entity = TestSessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"]), ForeignKey(entity = WordBatchEntity::class, parentColumns = ["id"], childColumns = ["batchId"])],
    indices = [Index(value = ["sessionId", "batchId"], unique = true), Index(value = ["sessionId", "selectionOrder"]), Index("batchId")],
)
data class SessionBatchEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val sessionId: Long, val batchId: Long, val selectionOrder: Int)

@Entity(
    tableName = "SessionWord",
    foreignKeys = [ForeignKey(entity = TestSessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"]), ForeignKey(entity = WordEntity::class, parentColumns = ["id"], childColumns = ["wordId"])],
    indices = [Index(value = ["sessionId", "wordId"], unique = true), Index(value = ["sessionId", "queueState", "queueOrder"]), Index("wordId")],
)
data class SessionWordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val wordId: Long,
    val displayOrder: Int,
    val requiredMeaningSnapshot: String?,
    val wasFirstRoundWrong: Boolean = false,
    val queueState: SessionWordQueueState,
    val queueOrder: Int,
    val lastAnsweredPhase: TestPhase? = null,
    val lastAnsweredRound: Int? = null,
    val lastResult: TestResult? = null,
)

@Entity(tableName = "TestRecord", indices = [Index(value = ["sessionId", "createdAt"]), Index(value = ["sessionId", "wordId"]), Index("wordId")])
data class TestRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val wordId: Long,
    val phase: TestPhase,
    val roundNumber: Int,
    val result: TestResult,
    val testMode: TestMode,
    val createdAt: Long,
)

@Entity(tableName = "WrongWord", indices = [Index(value = ["wordId"], unique = true), Index(value = ["status", "lastWrongAt"])])
data class WrongWordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val wordId: Long,
    val firstWrongAt: Long,
    val lastWrongAt: Long,
    val wrongCount: Int,
    val reviewWrongCount: Int = 0,
    val status: WrongWordStatus = WrongWordStatus.ACTIVE,
    val masteredAt: Long? = null,
)

@Entity(
    tableName = "WrongRecord",
    indices = [Index(value = ["testSessionId", "wordId", "recordType"], unique = true), Index(value = ["recordType", "wrongAt", "wordId"]), Index("wordId")],
)
data class WrongRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val wordId: Long,
    val testSessionId: Long,
    val wrongAt: Long,
    val recordType: WrongRecordType,
    val testMode: TestMode,
)

@Entity(tableName = "UndoSnapshot")
data class UndoSnapshotEntity(
    @PrimaryKey val sessionId: Long,
    val testRecordId: Long,
    val sessionBefore: String,
    val wordBefore: String,
    val wrongWordBefore: String?,
    val wrongWordDidNotExist: Boolean,
    val wrongRecordId: Long?,
    val wrongRecordCreated: Boolean,
    val createdAt: Long,
)

data class BatchRow(val id: Long, val batchName: String, val createdAt: Long, val wordCount: Int)
data class WrongWordBatchLink(val batchId: Long, val batchName: String, val batchCreatedAt: Long, val wordId: Long, val sortOrder: Int)
data class SessionWordWithWord(@Embedded val sessionWord: SessionWordEntity, @Relation(parentColumn = "wordId", entityColumn = "id") val word: WordEntity)
data class WrongWordRow(@Embedded val wrong: WrongWordEntity, @Relation(parentColumn = "wordId", entityColumn = "id") val word: WordEntity)
