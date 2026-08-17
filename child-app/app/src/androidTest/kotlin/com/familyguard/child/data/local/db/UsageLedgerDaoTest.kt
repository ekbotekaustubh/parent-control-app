package com.familyguard.child.data.local.db

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private const val DATE = "2026-08-16"

@RunWith(AndroidJUnit4::class)
class UsageLedgerDaoTest {

    private lateinit var db: ChildDatabase
    private lateinit var dao: UsageLedgerDao

    @Before
    fun createDb() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, ChildDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.usageLedgerDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun accrue_onNewRow_createsRowWithGivenDelta() = runBlocking {
        dao.accrue("com.example.app", DATE, deltaMinutes = 5, now = 1000)

        val row = dao.get("com.example.app", DATE)
        assertEquals(5, row?.durationMinutes)
        assertEquals(0, row?.lastSyncedDurationMinutes)
    }

    @Test
    fun accrue_isCumulative_notOverwriting() = runBlocking {
        dao.accrue("com.example.app", DATE, deltaMinutes = 5, now = 1000)
        dao.accrue("com.example.app", DATE, deltaMinutes = 3, now = 2000)
        dao.accrue("com.example.app", DATE, deltaMinutes = 2, now = 3000)

        val row = dao.get("com.example.app", DATE)
        assertEquals(10, row?.durationMinutes)
        assertEquals(3000L, row?.updatedAt)
    }

    @Test
    fun accrue_forDifferentDates_keepsSeparateTotals() = runBlocking {
        dao.accrue("com.example.app", "2026-08-15", deltaMinutes = 20, now = 1000)
        dao.accrue("com.example.app", "2026-08-16", deltaMinutes = 5, now = 2000)

        assertEquals(20, dao.get("com.example.app", "2026-08-15")?.durationMinutes)
        assertEquals(5, dao.get("com.example.app", "2026-08-16")?.durationMinutes)
    }

    @Test
    fun getUnsyncedRows_returnsOnlyRowsWithDurationAheadOfSyncedWatermark() = runBlocking {
        dao.upsert(UsageLedgerEntity("synced.app", DATE, durationMinutes = 10, lastSyncedDurationMinutes = 10, updatedAt = 100))
        dao.upsert(UsageLedgerEntity("unsynced.app", DATE, durationMinutes = 15, lastSyncedDurationMinutes = 5, updatedAt = 100))

        val unsynced = dao.getUnsyncedRows()

        assertEquals(1, unsynced.size)
        assertEquals("unsynced.app", unsynced.first().packageName)
    }

    @Test
    fun markSyncedUpTo_advancesWatermark_butNeverLowersIt() = runBlocking {
        dao.upsert(UsageLedgerEntity("app.a", DATE, durationMinutes = 30, lastSyncedDurationMinutes = 10, updatedAt = 100))

        dao.markSyncedUpTo("app.a", DATE, 20)
        assertEquals(20, dao.get("app.a", DATE)?.lastSyncedDurationMinutes)

        // A stale/lower sync confirmation must not regress the watermark.
        dao.markSyncedUpTo("app.a", DATE, 15)
        assertEquals(20, dao.get("app.a", DATE)?.lastSyncedDurationMinutes)

        dao.markSyncedUpTo("app.a", DATE, 30)
        assertEquals(30, dao.get("app.a", DATE)?.lastSyncedDurationMinutes)
    }

    @Test
    fun getForDate_returnsOnlyRowsForThatDate() = runBlocking {
        dao.accrue("app.a", "2026-08-15", 5, 100)
        dao.accrue("app.b", DATE, 7, 100)
        dao.accrue("app.c", DATE, 9, 100)

        val rows = dao.getForDate(DATE)
        assertEquals(2, rows.size)
        assertTrue(rows.all { it.usageDate == DATE })
    }

    @Test
    fun get_forNonexistentKey_returnsNull() = runBlocking {
        assertNull(dao.get("nobody.home", DATE))
    }
}
