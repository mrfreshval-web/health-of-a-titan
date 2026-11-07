package com.healthofatitan.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.healthofatitan.app.core.database.entity.TrainingSetEntity
import com.healthofatitan.app.core.database.entity.TrainingWorkoutEntity
import kotlinx.coroutines.flow.Flow

/** Workouts done in the HoT player and their sets (stream B, A40 §7). */
@Dao
abstract class TrainingLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertWorkout(row: TrainingWorkoutEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertSets(rows: List<TrainingSetEntity>)

    /** A workout and its sets in one transaction: the log never holds half a workout. */
    @Transaction
    open suspend fun save(workout: TrainingWorkoutEntity, sets: List<TrainingSetEntity>) {
        upsertWorkout(workout)
        upsertSets(sets)
    }

    @Query("SELECT * FROM training_workout WHERE user_id = :userId AND day_key = :dayKey ORDER BY finished_at DESC")
    abstract fun observeDay(userId: String, dayKey: String): Flow<List<TrainingWorkoutEntity>>

    @Query("SELECT * FROM training_workout WHERE user_id = :userId ORDER BY finished_at DESC LIMIT :limit")
    abstract suspend fun recent(userId: String, limit: Int): List<TrainingWorkoutEntity>

    @Query("SELECT * FROM training_set WHERE user_id = :userId AND workout_id = :workoutId ORDER BY step_index")
    abstract suspend fun sets(userId: String, workoutId: String): List<TrainingSetEntity>
}
