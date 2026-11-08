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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The consent `about_mentor` (A65 §5.2): off by default, every change is one journal row. */
class AboutMentorConsentStoreTest {

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
    fun `off by default, on and off again are kept as history`() = runBlocking {
        val dao = FakeConsentDao()
        val store = AboutMentorConsentStore(dao, now = { 1_000L })
        assertFalse(store.granted.first())
        store.set(true)
        assertTrue(store.granted.first())
        store.set(true) // no duplicate row
        store.set(false)
        assertFalse(store.granted.first())
        assertEquals(listOf(true, false), dao.rows.value.map { it.granted })
        assertTrue(dao.rows.value.all { it.consentId == "about_mentor" && it.consentVersion == 1 })
    }

    @Test
    fun `another consent does not grant it`() = runBlocking {
        val dao = FakeConsentDao()
        dao.insert(ConsentRecordEntity(userId = HotDatabase.LOCAL_USER, consentId = "age18", consentVersion = 1, granted = true, tsUtcMs = 1, tzOffsetMin = 0))
        assertFalse(AboutMentorConsentStore(dao).granted.first())
    }
}
