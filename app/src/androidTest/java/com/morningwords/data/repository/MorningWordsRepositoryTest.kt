package com.morningwords.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.morningwords.data.local.AppDatabase
import com.morningwords.domain.model.SessionStatus
import com.morningwords.domain.model.TestMode
import com.morningwords.domain.model.TestPhase
import com.morningwords.domain.model.TestResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MorningWordsRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: MorningWordsRepository
    private var now = 1_700_000_000_000L

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repository = MorningWordsRepository(database, clock = { now++ })
    }

    @After fun tearDown() = database.close()

    @Test fun importDeduplicatesAcrossBatchesAndCountsSharedWordsOncePerSession() = runTest {
        val first = repository.importBatch("第一批", "Maintain v. 保持\nachieve v. 实现")
        val second = repository.importBatch("第二批", " maintain v. 维持\navailable adj. 可用的")
        val dashboard = repository.dashboard.first()
        assertEquals(3, dashboard.wordCount)

        val result = repository.createDailySession(listOf(first, second), TestMode.STUDENT)
        val sessionId = (result as CreateSessionResult.Created).sessionId
        assertEquals(3, repository.loadSession(sessionId)?.session?.totalCount)
    }

    @Test fun firstRoundWrongPersistsAndFinalCheckCanComplete() = runTest {
        val batch = repository.importBatch("测试", "achieve")
        val sessionId = (repository.createDailySession(listOf(batch), TestMode.STUDENT) as CreateSessionResult.Created).sessionId

        var state = repository.answer(sessionId, TestResult.UNKNOWN)
        assertEquals(TestPhase.WRONG_LOOP, state.session.phase)
        assertEquals(1, repository.wrongWords.first().size)

        state = repository.answer(sessionId, TestResult.KNOW)
        assertEquals(TestPhase.FINAL_CHECK, state.session.phase)
        state = repository.answer(sessionId, TestResult.KNOW)
        assertEquals(SessionStatus.COMPLETED, state.session.status)
        assertEquals(1, repository.wrongWords.first().single().wrong.wrongCount)
    }

    @Test fun activeSessionProtectsItsSourceBatch() = runTest {
        val batch = repository.importBatch("不可删", "achieve")
        repository.createDailySession(listOf(batch), TestMode.PARENT)
        assertTrue(!repository.deleteBatch(batch))
    }
}

