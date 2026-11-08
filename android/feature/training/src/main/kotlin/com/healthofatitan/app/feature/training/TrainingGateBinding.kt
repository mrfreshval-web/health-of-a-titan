package com.healthofatitan.app.feature.training

import com.healthofatitan.app.core.database.ProfileSnapshot
import com.healthofatitan.app.core.database.ProfileStore
import com.healthofatitan.app.domain.safety.SafetyState
import com.healthofatitan.app.domain.training.TrainingLibrary
import com.healthofatitan.app.integration.whoop.day.TrainingGate
import com.healthofatitan.app.integration.whoop.safety.SafetyBus
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * S1b: «Сегодня» asks the same gates as the training screen (TG-01, TG-07, TG-05, «к врачу»), so a
 * step «тренировка» is never offered on a day the screen would hide the program. The same inputs and
 * the same fallbacks as [TrainingViewModel]: an unreadable bus is the strictest, a missing profile is
 * the empty one.
 */
class ProfileBusTrainingGate @Inject constructor(
    private val content: TrainingContent,
    private val profile: ProfileStore,
    private val bus: SafetyBus,
) : TrainingGate {

    override fun hidden(): Flow<Boolean> {
        val library = flow<TrainingLibrary?> { emit(content.library()) }.catch { emit(null) }
        val dictionary = flow { emit(content.profileDictionary()) }.catch { emit(null) }
        val snapshot = profile.profile.catch { emit(ProfileSnapshot()) }
        val safety = bus.state.catch { emit(SafetyState(unknown = true)) }
        return combine(library, dictionary, snapshot, safety) { lib, dict, p, s ->
            // Without the library the screen has no program either; the week step stays as it was.
            lib != null && TrainingSafety.hides(lib, TrainingSafety.guard(p.constraints, dict, s, lib.rules))
        }.distinctUntilChanged()
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class TrainingGateBinding {
    @Binds
    abstract fun trainingGate(gate: ProfileBusTrainingGate): TrainingGate
}
