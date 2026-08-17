package com.familyguard.child.data.local.db

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CachedScheduleDaoTest {

    private lateinit var db: ChildDatabase
    private lateinit var dao: CachedScheduleDao

    @Before
    fun createDb() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, ChildDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.cachedScheduleDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun replaceAll_clearsPreviousSchedulesNotInNewSet() = runBlocking {
        dao.upsertAll(listOf(CachedScheduleEntity("sched-stale", "block", "MON", "21:00", "07:00")))

        dao.replaceAll(listOf(CachedScheduleEntity("sched-fresh", "allow_only", "SAT", "09:00", "11:00")))

        val all = dao.getAll()
        assertEquals(1, all.size)
        assertEquals("sched-fresh", all.first().id)
    }

    @Test
    fun upsertAll_withSameId_replacesExistingRow() = runBlocking {
        dao.upsertAll(listOf(CachedScheduleEntity("sched-1", "block", "MON", "21:00", "07:00")))
        dao.upsertAll(listOf(CachedScheduleEntity("sched-1", "allow_only", "SAT,SUN", "09:00", "11:00")))

        val all = dao.getAll()
        assertEquals(1, all.size)
        assertEquals("allow_only", all.first().mode)
        assertEquals("SAT,SUN", all.first().daysOfWeek)
    }
}
