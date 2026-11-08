package com.healthofatitan.app.feature.nutrition

import android.content.Context
import com.healthofatitan.app.core.database.HotDatabase
import com.healthofatitan.app.core.database.ProfileSnapshot
import com.healthofatitan.app.core.database.ProfileStore
import com.healthofatitan.app.core.database.dao.NutritionDao
import com.healthofatitan.app.core.database.entity.NutritionEveningEntity
import com.healthofatitan.app.core.model.content.ContentJson
import com.healthofatitan.app.core.model.profile.ProfileCategory
import com.healthofatitan.app.core.model.profile.UserConstraints
import com.healthofatitan.app.domain.nutrition.DayCondition
import com.healthofatitan.app.domain.nutrition.EveningMark
import com.healthofatitan.app.domain.nutrition.FoldedProfile
import com.healthofatitan.app.domain.nutrition.Level
import com.healthofatitan.app.domain.nutrition.NutritionContent
import com.healthofatitan.app.domain.nutrition.NutritionDayPlan
import com.healthofatitan.app.domain.nutrition.NutritionEngine
import com.healthofatitan.app.domain.nutrition.ProfileFold
import com.healthofatitan.app.domain.nutrition.ProfileMode
import com.healthofatitan.app.integration.whoop.day.DayPlanRepository
import com.healthofatitan.app.integration.whoop.day.DayView
import com.healthofatitan.app.integration.whoop.safety.SafetyBus
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/** The compiled content of A41 (assets/content, written by `contentCompile`). Null = not readable. */
fun interface NutritionContentSource {
    fun load(): NutritionContent?
}

/**
 * Everything the food screens show.
 *
 * @property eveningDue the one-tap evening mark is asked (after 18:00, not in ed_care)
 * @property edCareActive `ED_CARE` is on in the safety bus: a withdrawal of S4 asks
 *   «Оставить бережный режим еды?» once (D-058)
 */
data class NutritionState(
    val content: NutritionContent? = null,
    val profile: ProfileSnapshot = ProfileSnapshot(),
    val plan: NutritionDayPlan? = null,
    val chips: Set<DayCondition> = emptySet(),
    val evening: EveningMark? = null,
    val eveningDue: Boolean = false,
    /** The highest level the profile allows (safety only lowers, A41 §4). */
    val levelCap: Level = Level.PRECISE,
    val loaded: Boolean = false,
    val edCareActive: Boolean = false,
) {
    val chosenLevel: Level get() = Level.of(profile.nutritionLevel) ?: Level.BASE
}

/**
 * Food of the day for the app (A41 §9): the profile (S4, [ProfileStore]), the stored day of the
 * A09 engine, the circumstance chips and «Заменить» taps go into the pure [NutritionEngine].
 * Chips and taps live in memory for the day; the evening mark is stored.
 */
@Singleton
class NutritionRepository @Inject constructor(
    private val source: NutritionContentSource,
    private val profiles: ProfileStore,
    private val days: DayPlanRepository,
    private val dao: NutritionDao,
    private val bus: SafetyBus,
) {
    private val user = HotDatabase.LOCAL_USER
    private val chips = MutableStateFlow(DayChips())

    private val content: Flow<NutritionContent?> = flow { emit(source.load()) }.flowOn(Dispatchers.Default)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun state(clock: Flow<LocalDateTime>): Flow<NutritionState> {
        val today = clock.map { it.toLocalDate() }.distinctUntilChanged()
        val evening = today.flatMapLatest { d -> dao.observeEvening(user, d.toString()) }
        val safety = bus.state.map { it.edCare.active }.distinctUntilChanged()
        val inputs = combine(content, profiles.profile, days.latest, chips) { c, p, d, ch -> Inputs(c, p, d, ch) }
        return combine(inputs, clock, evening, safety) { i, now, ev, ed -> build(i, now, EveningMark.of(ev?.mark), ed) }
    }

    private fun build(i: Inputs, now: LocalDateTime, evening: EveningMark?, edCare: Boolean): NutritionState {
        val today = now.toLocalDate()
        val dayChips = i.chips.takeIf { it.day == today } ?: DayChips(today)
        val c = i.content ?: return NutritionState(profile = i.profile, loaded = true, edCareActive = edCare)
        val folded = ProfileFold.fold(i.profile.constraints, c.profile, i.profile.consented).underEdCare(edCare)
        val chosen = Level.of(i.profile.nutritionLevel) ?: Level.BASE
        val plan = NutritionEngine.plan(DayFacts.input(i.day, today, chosen, folded, dayChips.conditions, dayChips.replaced), c)
        return NutritionState(
            content = c,
            profile = i.profile,
            plan = plan,
            chips = dayChips.conditions,
            evening = evening,
            eveningDue = now.hour >= EVENING_FROM_HOUR && plan.mode != ProfileMode.ED_CARE,
            levelCap = folded.levelCap,
            loaded = true,
            edCareActive = edCare,
        )
    }

    fun toggleChip(condition: DayCondition, today: LocalDate = LocalDate.now()) = chips.update { cur ->
        val d = if (cur.day == today) cur else DayChips(today)
        d.copy(conditions = if (condition in d.conditions) d.conditions - condition else d.conditions + condition)
    }

    /** «Заменить»: the next library meal of that slot (the filter never relaxes). */
    fun replace(slot: String, today: LocalDate = LocalDate.now()) = chips.update { cur ->
        val d = if (cur.day == today) cur else DayChips(today)
        d.copy(replaced = d.replaced + (slot to (d.replaced[slot] ?: 0) + 1))
    }

    suspend fun setEvening(mark: EveningMark, today: LocalDate = LocalDate.now()) =
        dao.upsertEvening(NutritionEveningEntity(user, today.toString(), mark.code, System.currentTimeMillis()))

    suspend fun setConsent(granted: Boolean) = profiles.setConsent(granted)

    /**
     * Withdraws S4 (D-058). [keepEdCare] counts only while `ED_CARE` is on in the bus: then one
     * mark «режим включён» stays; otherwise everything is deleted.
     */
    suspend fun withdrawConsent(keepEdCare: Boolean) =
        profiles.withdrawConsent(keepEdCare = keepEdCare && bus.current().edCare.active)

    suspend fun save(constraints: UserConstraints) = profiles.save(constraints)

    suspend fun delete(category: ProfileCategory) = profiles.delete(category)

    suspend fun deleteAll() = profiles.deleteAll()

    suspend fun setLevel(level: Level) = profiles.setNutritionLevel(level.code)

    private data class Inputs(val content: NutritionContent?, val profile: ProfileSnapshot, val day: DayView?, val chips: DayChips)

    private data class DayChips(
        val day: LocalDate = LocalDate.now(),
        val conditions: Set<DayCondition> = emptySet(),
        val replaced: Map<String, Int> = emptyMap(),
    )

    companion object {
        const val EVENING_FROM_HOUR = 18
    }
}

@Module
@InstallIn(SingletonComponent::class)
object NutritionModule {

    /** A41 content from the content pipeline (assets/content/nutrition, assets/content/profile). */
    @Provides
    @Singleton
    fun provideContent(@ApplicationContext context: Context): NutritionContentSource {
        val cached by lazy {
            runCatching {
                NutritionContent.parse(
                    NutritionContent.ASSETS.mapValues { (_, path) ->
                        context.assets.open(path).bufferedReader().use { ContentJson.parse(it.readText()) }
                    },
                )
            }.getOrNull()
        }
        return NutritionContentSource { cached }
    }
}

/**
 * `ED_CARE` of the safety bus folds into the food of the day even without the profile (D-058):
 * after S4 is withdrawn with «Да» the one kept mark still gives «Основа» without numbers and
 * without photo feedback. The bus only makes it stricter, never softer.
 */
internal fun FoldedProfile.underEdCare(edCareActive: Boolean): FoldedProfile =
    if (!edCareActive || mode == ProfileMode.ED_CARE) {
        this
    } else {
        copy(mode = ProfileMode.ED_CARE, levelCap = Level.BASE, hideNumbers = true, photoFeedbackOff = true)
    }
