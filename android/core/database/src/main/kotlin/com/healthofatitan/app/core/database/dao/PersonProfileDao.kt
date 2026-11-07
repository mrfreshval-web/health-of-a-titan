package com.healthofatitan.app.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.healthofatitan.app.core.database.entity.PersonProfileEntity
import kotlinx.coroutines.flow.Flow

/** The profile «Я» (A61 §2.1): address form, year of birth, sex for calculations. */
@Dao
interface PersonProfileDao {

    @Query("SELECT * FROM person_profile WHERE user_id = :userId")
    fun observe(userId: String): Flow<PersonProfileEntity?>

    @Query("SELECT * FROM person_profile WHERE user_id = :userId")
    suspend fun get(userId: String): PersonProfileEntity?

    @Upsert
    suspend fun upsert(row: PersonProfileEntity)
}
