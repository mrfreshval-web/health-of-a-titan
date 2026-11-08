package com.healthofatitan.app.core.database

import com.healthofatitan.app.core.database.dao.ConsentDao
import com.healthofatitan.app.core.database.entity.ConsentRecordEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals

/** A68 v1: one tick writes `unified` and the parts it absorbs; nothing separate is granted. */
class UnifiedConsentStoreTest {

    private class FakeConsentDao : ConsentDao {
        val rows = MutableStateFlow<List<ConsentRecordEntity>>(emptyList())

        override suspend fun insert(record: ConsentRecordEntity): Long {
            val id = rows.value.size + 1L
            rows.value = rows.value + record.copy(id = id)
            return id
        }

        override fun observeCurrent(userId: String): Flow<List<ConsentRecordEntity>> = rows.map { all ->
            all.filter { it.userId == userId }.groupBy { it.consentId }.values.map { group -> group.maxBy { it.id } }
        }

        override suspend fun latest(userId: String, consentId: String): ConsentRecordEntity? =
            rows.value.filter { it.userId == userId && it.consentId == consentId }.maxByOrNull { it.id }
    }

    @Test
    fun `asks until accepted, then grants unified and its parts at one time`() = runBlocking {
        val dao = FakeConsentDao()
        val store = UnifiedConsentStore(dao, now = { 5_000L })
        assertEquals(ConsentGate.ASK, store.gate.first())

        store.accept()

        assertEquals(ConsentGate.GRANTED, store.gate.first())
        assertEquals(listOf("unified", "profile_s4", "mentor_ai"), dao.rows.value.map { it.consentId })
        assertEquals(setOf(5_000L), dao.rows.value.map { it.tsUtcMs }.toSet())
        // the separate consents stay separate (A68 §2)
        assertEquals(emptyList(), dao.rows.value.filter { it.consentId in setOf("metrics_ai", "ed_care_keep", "roots", "about_mentor") })
    }

    @Test
    fun `a part already granted is not written twice`() = runBlocking {
        val dao = FakeConsentDao()
        dao.insert(ConsentRecordEntity(userId = HotDatabase.LOCAL_USER, consentId = ProfileStore.CONSENT_ID, consentVersion = 1, granted = true, tsUtcMs = 1, tzOffsetMin = 0))
        UnifiedConsentStore(dao, now = { 5_000L }).accept()
        assertEquals(1, dao.rows.value.count { it.consentId == ProfileStore.CONSENT_ID })
    }

    @Test
    fun `a tick on an older text asks again`() = runBlocking {
        val dao = FakeConsentDao()
        dao.insert(ConsentRecordEntity(userId = HotDatabase.LOCAL_USER, consentId = UnifiedConsentStore.CONSENT_ID, consentVersion = UnifiedConsentStore.VERSION - 1, granted = true, tsUtcMs = 1, tzOffsetMin = 0))
        val store = UnifiedConsentStore(dao, now = { 5_000L })
        assertEquals(ConsentGate.ASK, store.gate.first())

        store.accept()

        assertEquals(ConsentGate.GRANTED, store.gate.first())
    }
}
