package com.healthofatitan.app.feature.path

import com.healthofatitan.app.core.database.HotDatabase
import com.healthofatitan.app.core.database.dao.PathDao
import com.healthofatitan.app.domain.path.GoalLayer
import com.healthofatitan.app.domain.path.GoalLifecycle
import com.healthofatitan.app.domain.today.week.PathWeek
import com.healthofatitan.app.feature.path.logic.StageWeek
import com.healthofatitan.app.integration.whoop.week.WeekPathBridge
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject

/**
 * The bridge Путь → Неделя (A67 §2.1 ①, С-3): the goal of the Path (the one goal in the layer
 * «Путь», P-22) gives the goals of the week through [StageWeek]. It reads `path_*` directly, not
 * [PathRepository], so the week does not depend on the day plan through the Path. The lever as
 * the experiment of the week ([WeekPathBridge.lever]) and the Sunday step of the Titan of the
 * horizon are slice С4.
 */
class PathWeekBridge @Inject constructor(private val dao: PathDao) : WeekPathBridge {
    private val user = HotDatabase.LOCAL_USER

    override fun stageGoals(): Flow<PathWeek> =
        combine(dao.observeGoals(user), dao.observeComponents(user)) { goals, parts ->
            val byGoal = parts.groupBy { it.goalId }
            val goal = goals.asSequence()
                .mapNotNull { toGoal(it, byGoal[it.id].orEmpty()) }
                .firstOrNull { GoalLifecycle.layer(it.status) == GoalLayer.PATH }
            StageWeek.of(goal, PathFeatureFlags.current)
        }.distinctUntilChanged()
}

/** Binds the bridge for the optional seam of `:integration:whoop` (`WeekPathBridgeModule`). */
@Module
@InstallIn(SingletonComponent::class)
abstract class PathWeekBridgeModule {
    @Binds
    abstract fun bindWeekPathBridge(impl: PathWeekBridge): WeekPathBridge
}
