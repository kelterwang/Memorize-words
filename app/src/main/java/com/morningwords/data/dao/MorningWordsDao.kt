package com.morningwords.data.dao

import androidx.room.*
import com.morningwords.data.entity.*
import com.morningwords.domain.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MorningWordsDao {
    @Query("SELECT * FROM Word WHERE normalizedWord = :normalized LIMIT 1")
    suspend fun findWord(normalized: String): WordEntity?

    @Query("SELECT * FROM Word WHERE id = :id LIMIT 1")
    suspend fun word(id: Long): WordEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertWord(value: WordEntity): Long
    @Update suspend fun updateWord(value: WordEntity)
    @Insert suspend fun insertBatch(value: WordBatchEntity): Long
    @Insert suspend fun insertBatchWord(value: BatchWordEntity): Long
    @Update suspend fun updateBatchWord(value: BatchWordEntity)
    @Query("DELETE FROM BatchWord WHERE id=:id")
    suspend fun deleteBatchWord(id: Long)
    @Query("UPDATE WordBatch SET batchName=:name WHERE id=:batchId")
    suspend fun renameBatch(batchId: Long, name: String): Int

    @Query("SELECT b.id, b.batchName, b.createdAt, COUNT(bw.id) AS wordCount FROM WordBatch b LEFT JOIN BatchWord bw ON bw.batchId=b.id GROUP BY b.id ORDER BY b.createdAt DESC")
    fun observeBatches(): Flow<List<BatchRow>>

    @Query("SELECT b.id, b.batchName, b.createdAt, COUNT(bw.id) AS wordCount FROM WordBatch b LEFT JOIN BatchWord bw ON bw.batchId=b.id GROUP BY b.id ORDER BY b.createdAt DESC")
    suspend fun getBatches(): List<BatchRow>

    @Query("SELECT w.* FROM Word w JOIN BatchWord bw ON bw.wordId=w.id WHERE bw.batchId=:batchId ORDER BY bw.sortOrder")
    suspend fun wordsInBatch(batchId: Long): List<WordEntity>

    @Query("SELECT bw.* FROM BatchWord bw WHERE bw.batchId IN (:batchIds) ORDER BY CASE bw.batchId WHEN :firstBatchId THEN 0 ELSE 1 END, bw.sortOrder")
    suspend fun selectedBatchWords(batchIds: List<Long>, firstBatchId: Long): List<BatchWordEntity>

    @Query("SELECT * FROM BatchWord WHERE batchId=:batchId ORDER BY sortOrder")
    suspend fun batchWords(batchId: Long): List<BatchWordEntity>

    @Query("SELECT * FROM BatchWord ORDER BY id")
    suspend fun allBatchWords(): List<BatchWordEntity>

    @Query("SELECT * FROM Word WHERE id IN (:ids)") suspend fun wordsByIds(ids: List<Long>): List<WordEntity>
    @Query("SELECT * FROM TestSession WHERE status='IN_PROGRESS' ORDER BY updatedAt DESC LIMIT 1")
    suspend fun activeSession(): TestSessionEntity?
    @Query("SELECT * FROM TestSession WHERE id=:id") suspend fun session(id: Long): TestSessionEntity?
    @Insert suspend fun insertSession(value: TestSessionEntity): Long
    @Update suspend fun updateSession(value: TestSessionEntity)
    @Insert suspend fun insertSessionBatches(values: List<SessionBatchEntity>)
    @Insert suspend fun insertSessionWords(values: List<SessionWordEntity>)
    @Update suspend fun updateSessionWord(value: SessionWordEntity)
    @Update suspend fun updateSessionWords(values: List<SessionWordEntity>)

    @Transaction
    @Query("SELECT * FROM SessionWord WHERE sessionId=:sessionId ORDER BY queueOrder, displayOrder")
    suspend fun sessionWords(sessionId: Long): List<SessionWordWithWord>

    @Insert suspend fun insertTestRecord(value: TestRecordEntity): Long
    @Query("SELECT * FROM WrongWord WHERE wordId=:wordId") suspend fun wrongWord(wordId: Long): WrongWordEntity?
    @Insert suspend fun insertWrongWord(value: WrongWordEntity): Long
    @Update suspend fun updateWrongWord(value: WrongWordEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertWrongRecord(value: WrongRecordEntity): Long

    @Transaction
    @Query("SELECT * FROM WrongWord WHERE status='ACTIVE' ORDER BY lastWrongAt DESC")
    fun observeWrongWords(): Flow<List<WrongWordRow>>

    @Query("""
        SELECT b.id AS batchId, b.batchName AS batchName, b.createdAt AS batchCreatedAt,
               bw.wordId AS wordId, bw.sortOrder AS sortOrder
        FROM WordBatch b
        JOIN BatchWord bw ON bw.batchId=b.id
        JOIN WrongWord ww ON ww.wordId=bw.wordId AND ww.status='ACTIVE'
        ORDER BY b.createdAt DESC, b.id DESC, bw.sortOrder ASC
    """)
    fun observeWrongWordBatchLinks(): Flow<List<WrongWordBatchLink>>

    @Transaction
    @Query("SELECT * FROM WrongWord WHERE status='ACTIVE' ORDER BY lastWrongAt DESC")
    suspend fun activeWrongWords(): List<WrongWordRow>

    @Query("SELECT COUNT(*) FROM WrongWord WHERE status='ACTIVE'") fun observeWrongCount(): Flow<Int>
    @Query("SELECT COUNT(*) FROM WordBatch") fun observeBatchCount(): Flow<Int>
    @Query("SELECT COUNT(DISTINCT wordId) FROM BatchWord") fun observeWordCount(): Flow<Int>

    @Query("""
        SELECT wr.wordId
        FROM WrongRecord wr
        JOIN WrongWord ww ON ww.wordId=wr.wordId
        JOIN BatchWord bw ON bw.wordId=wr.wordId
        WHERE wr.recordType='FIRST_ROUND_WRONG'
          AND wr.wrongAt BETWEEN :start AND :end
          AND ww.status='ACTIVE'
          AND bw.batchId IN (:batchIds)
        GROUP BY wr.wordId
        ORDER BY MAX(wr.wrongAt) DESC
    """)
    suspend fun reviewWordIds(start: Long, end: Long, batchIds: List<Long>): List<Long>

    @Query("SELECT COUNT(*) FROM SessionBatch sb JOIN TestSession ts ON ts.id=sb.sessionId WHERE sb.batchId=:batchId AND ts.status='IN_PROGRESS'")
    suspend fun activeBatchReferences(batchId: Long): Int
    @Query("DELETE FROM SessionBatch WHERE batchId=:batchId AND sessionId IN (SELECT id FROM TestSession WHERE status!='IN_PROGRESS')")
    suspend fun detachHistoricBatch(batchId: Long)
    @Query("DELETE FROM WordBatch WHERE id=:batchId") suspend fun deleteBatchRow(batchId: Long)
    @Query("UPDATE TestSession SET status='ABANDONED', updatedAt=:now WHERE id=:sessionId AND status='IN_PROGRESS'")
    suspend fun abandon(sessionId: Long, now: Long)
}
