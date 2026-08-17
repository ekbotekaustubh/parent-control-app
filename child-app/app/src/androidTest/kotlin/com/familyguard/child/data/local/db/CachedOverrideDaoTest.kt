package com.familyguard.child.data.local.db

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CachedOverrideDaoTest {

    private lateinit var db: ChildDatabase
    private lateinit var dao: CachedOverrideDao

    @Before
    fun createDb() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, ChildDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.cachedOverrideDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun getActive_returnsOnlyRowsNotYetExpired() = runBlocking {
        dao.upsertAll(
            listOf(
                CachedOverrideEntity("com.example.active", extraMinutes = 15, expiresAtMillis = 2_000L),
                CachedOverrideEntity("com.example.expired", extraMinutes = 10, expiresAtMillis = 500L),
            ),
        )

        val active = dao.getActive(nowMillis = 1_000L)

        assertEquals(1, active.size)
        assertEquals("com.example.active", active.first().packageName)
    }

    @Test
    fun upsertAll_withSamePackageName_replacesExistingRow() = runBlocking {
        dao.upsertAll(listOf(CachedOverrideEntity("com.example.a", extraMinutes = 10, expiresAtMillis = 5_000L)))
        dao.upsertAll(listOf(CachedOverrideEntity("com.example.a", extraMinutes = 20, expiresAtMillis = 6_000L)))

        val active = dao.getActive(nowMillis = 0L)

        assertEquals(1, active.size)
        assertEquals(20, active.first().extraMinutes)
    }

    @Test
    fun replaceAll_clearsPreviousOverridesNotInNewSet() = runBlocking {
        dao.upsertAll(listOf(CachedOverrideEntity("com.example.stale", extraMinutes = 10, expiresAtMillis = 5_000L)))

        dao.replaceAll(listOf(CachedOverrideEntity("com.example.fresh", extraMinutes = 15, expiresAtMillis = 5_000L)))

        val active = dao.getActive(nowMillis = 0L)
        assertEquals(1, active.size)
        assertTrue(active.none { it.packageName == "com.example.stale" })
    }
}
