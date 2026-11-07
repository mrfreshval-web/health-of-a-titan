package com.healthofatitan.app.training

import com.healthofatitan.app.R
import com.healthofatitan.app.feature.training.WorkoutNotificationIcon
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Stream B: the workout notification uses the app's monochrome mark. */
@Module
@InstallIn(SingletonComponent::class)
object TrainingModule {
    @Provides
    fun provideWorkoutNotificationIcon(): WorkoutNotificationIcon = WorkoutNotificationIcon { R.drawable.ic_stat_hot }
}
