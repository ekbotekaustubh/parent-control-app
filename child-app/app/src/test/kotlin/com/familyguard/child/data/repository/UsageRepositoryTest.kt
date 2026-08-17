package com.familyguard.child.data.repository

import com.familyguard.child.data.local.SyncMetadataStore
import com.familyguard.child.data.local.db.SyncQueueDao
import com.familyguard.child.data.local.db.SyncQueueEntity
import com.familyguard.child.data.local.db.UsageLedgerDao
import com.familyguard.child.data.local.db.UsageLedgerEntity
import com.familyguard.child.data.remote.ChildApiService
import com.familyguard.shared.dto.UsageSyncRequest
import com.familyguard.shared.dto.UsageSyncResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val DATE = "2026-08-16"

/** In-memory fake — no Room/instrumentation needed, per the spec's "fake DAO" test style. */
private class FakeUsageLedgerDao : UsageLedgerDao {
    val rows = mutableMapOf<Pair<String, String>, UsageLedgerEntity>()
    private val flow = MutableStateFlow<List<UsageLedgerEntity>>(emptyList())

    override fun observeForDate(usageDate: String): Flow<List<UsageLedgerEntity>> = flow

    override suspend fun getForDate(usageDate: String): List<UsageLedgerEntity> =
        rows.values.filter { it.usageDate == usageDate }

    override suspend fun get(packageName: String, usageDate: String): UsageLedgerEntity? =
        rows[packageName to usageDate]

    override suspend fun upsert(entity: UsageLedgerEntity) {
        rows[entity.packageName to entity.usageDate] = entity
        flow.value = rows.values.toList()
    }

    override suspend fun getUnsyncedRows(): List<UsageLedgerEntity> =
        rows.values.filter { it.durationMinutes > it.lastSyncedDurationMinutes }

    override suspend fun markSyncedUpTo(packageName: String, usageDate: String, syncedValue: Int) {
        val key = packageName to usageDate
        val existing = rows[key] ?: return
        if (existing.lastSyncedDurationMinutes < syncedValue) {
            rows[key] = existing.copy(lastSyncedDurationMinutes = syncedValue)
        }
    }
}

private class FakeSyncQueueDao : SyncQueueDao {
    val queue = mutableListOf<SyncQueueEntity>()
    private var nextId = 1L

    override suspend fun enqueue(entity: SyncQueueEntity): Long {
        val withId = entity.copy(id = nextId++)
        queue.add(withId)
        return withId.id
    }

    override suspend fun getAll(): List<SyncQueueEntity> = queue.toList()

    override suspend fun delete(entity: SyncQueueEntity) {
        queue.remove(entity)
    }

    override suspend fun deleteById(id: Long) {
        queue.removeAll { it.id == id }
    }

    override suspend fun incrementAttempts(id: Long) {
        val idx = queue.indexOfFirst { it.id == id }
        if (idx >= 0) queue[idx] = queue[idx].copy(attempts = queue[idx].attempts + 1)
    }

    override suspend fun clearAll() {
        queue.clear()
    }

    override suspend fun deleteForKey(packageName: String, usageDate: String) {
        queue.removeAll { it.packageName == packageName && it.usageDate == usageDate }
    }
}

class UsageRepositoryTest {

    private lateinit var ledgerDao: FakeUsageLedgerDao
    private lateinit var syncQueueDao: FakeSyncQueueDao
    private lateinit var api: ChildApiService
    private lateinit var syncMetadataStore: SyncMetadataStore
    private lateinit var repository: UsageRepository

    @Before
    fun setUp() {
        ledgerDao = FakeUsageLedgerDao()
        syncQueueDao = FakeSyncQueueDao()
        api = mockk(relaxed = true)
        syncMetadataStore = mockk(relaxed = true)
        repository = UsageRepository(api, ledgerDao, syncQueueDao, syncMetadataStore)
    }

    @Test
    fun `accrue creates a new ledger row on first observation`() = runTest {
        repository.accrueForegroundTime("com.example.app", DATE, 2)

        val row = ledgerDao.get("com.example.app", DATE)
        assertEquals(2, row?.durationMinutes)
        assertEquals(0, row?.lastSyncedDurationMinutes)
    }

    @Test
    fun `accrue is additive across multiple calls`() = runTest {
        repository.accrueForegroundTime("com.example.app", DATE, 2)
        repository.accrueForegroundTime("com.example.app", DATE, 3)

        assertEquals(5, ledgerDao.get("com.example.app", DATE)?.durationMinutes)
    }

    @Test
    fun `accrue ignores non-positive deltas`() = runTest {
        repository.accrueForegroundTime("com.example.app", DATE, 0)
        assertEquals(null, ledgerDao.get("com.example.app", DATE))
    }

    @Test
    fun `enqueuePendingSnapshots enqueues only rows with unsynced deltas`() = runTest {
        ledgerDao.upsert(UsageLedgerEntity("synced.app", DATE, durationMinutes = 10, lastSyncedDurationMinutes = 10, updatedAt = 0))
        ledgerDao.upsert(UsageLedgerEntity("unsynced.app", DATE, durationMinutes = 15, lastSyncedDurationMinutes = 5, updatedAt = 0))

        repository.enqueuePendingSnapshots()

        assertEquals(1, syncQueueDao.queue.size)
        assertEquals("unsynced.app", syncQueueDao.queue.first().packageName)
        assertEquals(15, syncQueueDao.queue.first().durationMinutes) // cumulative snapshot, not a delta
    }

    @Test
    fun `drainSyncQueue with empty queue succeeds trivially without calling the API`() = runTest {
        val result = repository.drainSyncQueue()
        assertTrue(result)
        coVerify(exactly = 0) { api.syncUsage(any()) }
    }

    @Test
    fun `drainSyncQueue sends the max cumulative value per key and clears the queue on success`() = runTest {
        ledgerDao.upsert(UsageLedgerEntity("app.a", DATE, durationMinutes = 20, lastSyncedDurationMinutes = 0, updatedAt = 0))
        syncQueueDao.enqueue(SyncQueueEntity(packageName = "app.a", usageDate = DATE, durationMinutes = 10, createdAt = 0))
        syncQueueDao.enqueue(SyncQueueEntity(packageName = "app.a", usageDate = DATE, durationMinutes = 20, createdAt = 1))
        coEvery { api.syncUsage(any()) } returns UsageSyncResponse(acceptedCount = 1, serverTimeUtc = "now")

        val result = repository.drainSyncQueue()

        assertTrue(result)
        assertTrue(syncQueueDao.queue.isEmpty())
        assertEquals(20, ledgerDao.get("app.a", DATE)?.lastSyncedDurationMinutes)
        coVerify(exactly = 1) {
            api.syncUsage(match<UsageSyncRequest> { it.events.size == 1 && it.events.first().durationMinutes == 20 })
        }
    }

    @Test
    fun `drainSyncQueue leaves the queue intact and returns false on API failure`() = runTest {
        syncQueueDao.enqueue(SyncQueueEntity(packageName = "app.a", usageDate = DATE, durationMinutes = 10, createdAt = 0))
        coEvery { api.syncUsage(any()) } throws RuntimeException("network down")

        val result = repository.drainSyncQueue()

        assertFalse(result)
        assertEquals(1, syncQueueDao.queue.size)
    }

    @Test
    fun `markSyncedUpTo never lowers an already-higher synced watermark`() = runTest {
        ledgerDao.upsert(UsageLedgerEntity("app.a", DATE, durationMinutes = 30, lastSyncedDurationMinutes = 25, updatedAt = 0))
        ledgerDao.markSyncedUpTo("app.a", DATE, 20) // stale/lower value, should be ignored
        assertEquals(25, ledgerDao.get("app.a", DATE)?.lastSyncedDurationMinutes)
    }
}
