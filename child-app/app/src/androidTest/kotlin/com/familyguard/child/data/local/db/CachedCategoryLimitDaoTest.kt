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
class CachedCategoryLimitDaoTest {

    private lateinit var db: ChildDatabase
    private lateinit var dao: CachedCategoryLimitDao

    @Before
    fun createDb() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, ChildDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.cachedCategoryLimitDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun upsertAll_withSameCategory_replacesExistingRow() = runBlocking {
        dao.upsertAll(listOf(CachedCategoryLimitEntity("social", dailyLimitMinutes = 60)))
        dao.upsertAll(listOf(CachedCategoryLimitEntity("social", dailyLimitMinutes = 30)))

        val all = dao.getAll()
        assertEquals(1, all.size)
        assertEquals(30, all.first().dailyLimitMinutes)
    }

    @Test
    fun replaceAll_clearsPreviousLimitsNotInNewSet() = runBlocking {
        dao.upsertAll(listOf(CachedCategoryLimitEntity("stale", dailyLimitMinutes = 10)))

        dao.replaceAll(listOf(CachedCategoryLimitEntity("fresh", dailyLimitMinutes = 20)))

        val all = dao.getAll()
        assertEquals(1, all.size)
        assertEquals("fresh", all.first().category)
    }
}
