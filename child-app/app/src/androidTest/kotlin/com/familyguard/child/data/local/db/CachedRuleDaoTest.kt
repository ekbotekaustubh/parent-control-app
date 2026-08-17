package com.familyguard.child.data.local.db

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CachedRuleDaoTest {

    private lateinit var db: ChildDatabase
    private lateinit var dao: CachedRuleDao

    @Before
    fun createDb() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, ChildDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.cachedRuleDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun upsertAll_thenGetAll_returnsInsertedRows() = runBlocking {
        val rules = listOf(
            CachedRuleEntity("com.example.a", "block", null, configVersion = 1, updatedAt = 100),
            CachedRuleEntity("com.example.b", "allow", 45, configVersion = 1, updatedAt = 100),
        )
        dao.upsertAll(rules)

        val all = dao.getAll()
        assertEquals(2, all.size)
        assertTrue(all.any { it.packageName == "com.example.a" && it.ruleType == "block" })
        assertTrue(all.any { it.packageName == "com.example.b" && it.dailyLimitMinutes == 45 })
    }

    @Test
    fun upsertAll_withSamePackageName_replacesExistingRow() = runBlocking {
        dao.upsertAll(listOf(CachedRuleEntity("com.example.a", "block", null, configVersion = 1, updatedAt = 100)))
        dao.upsertAll(listOf(CachedRuleEntity("com.example.a", "allow", 30, configVersion = 2, updatedAt = 200)))

        val all = dao.getAll()
        assertEquals(1, all.size)
        assertEquals("allow", all.first().ruleType)
        assertEquals(30, all.first().dailyLimitMinutes)
        assertEquals(2L, all.first().configVersion)
    }

    @Test
    fun replaceAll_clearsPreviousRulesNotInNewSet() = runBlocking {
        dao.upsertAll(listOf(CachedRuleEntity("com.example.stale", "block", null, configVersion = 1, updatedAt = 100)))

        dao.replaceAll(listOf(CachedRuleEntity("com.example.fresh", "allow", 20, configVersion = 2, updatedAt = 200)))

        val all = dao.getAll()
        assertEquals(1, all.size)
        assertEquals("com.example.fresh", all.first().packageName)
    }

    @Test
    fun observeAll_emitsUpdatesAfterUpsert() = runBlocking {
        assertTrue(dao.observeAll().first().isEmpty())

        dao.upsertAll(listOf(CachedRuleEntity("com.example.a", "block", null, configVersion = 1, updatedAt = 100)))

        val emitted = dao.observeAll().first()
        assertEquals(1, emitted.size)
    }
}
