package com.healthofatitan.app.integration.healthconnect

import android.content.Context
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.healthofatitan.app.domain.engine.Steps
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Singleton

/** What «Сегодня» and the header ring know about the phone's steps right now. */
sealed interface PhoneSteps {
    /** Health Connect is not on this phone (or needs an update): the app works without steps. */
    data object Unavailable : PhoneSteps

    /** Health Connect is there, READ_STEPS is not granted yet: ask in place, once, with a reason. */
    data object NeedsPermission : PhoneSteps

    /**
     * @param today phone steps since local midnight
     * @param goal the personal median of earlier days ([Steps.goal])
     * @param inSlot the most steps in a walk-long stretch around the planned walk, null when not asked
     */
    data class Ready(val today: Int, val goal: Int, val inSlot: Int? = null) : PhoneSteps {
        val fraction: Float get() = Steps.fraction(today, goal)
    }
}

/**
 * Where to look for the planned walk (A35 §4): the slot widened like WHOOP's ±3 h match, and the
 * walk's planned length — the best stretch of that length inside the window is what counts.
 */
data class WalkWindow(val from: Instant, val to: Instant, val minutes: Int)

/** Reads phone step records; [HealthConnectSteps] on the device, a fake in tests. */
interface PhoneStepsSource {
    fun available(): Boolean

    suspend fun granted(): Boolean

    /** Phone step records between [from] and [to]; WHOOP's records are never returned (D-024). */
    suspend fun read(from: Instant, to: Instant): List<StepChunk>
}

/**
 * Health Connect, steps only (D-024, A35 §2, A18 И3b.5): `READ_STEPS` and nothing else. Records
 * come from the phone — device type «phone» or the system counter (`android`) — and never from
 * WHOOP, so the strap's steps are not counted twice. Read in memory, never stored (R-11).
 */
class HealthConnectSteps(private val context: Context) : PhoneStepsSource {

    private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    override fun available(): Boolean =
        runCatching { HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE }.getOrDefault(false)

    override suspend fun granted(): Boolean =
        runCatching { client.permissionController.getGrantedPermissions().containsAll(PERMISSIONS) }.getOrDefault(false)

    override suspend fun read(from: Instant, to: Instant): List<StepChunk> {
        val out = mutableListOf<StepChunk>()
        var token: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(from, to),
                    pageSize = PAGE,
                    pageToken = token,
                ),
            )
            response.records.filter(::fromPhone).mapTo(out) { r ->
                StepChunk(r.metadata.dataOrigin.packageName, r.startTime, r.endTime, r.count)
            }
            token = response.pageToken
        } while (token != null)
        return out
    }

    private fun fromPhone(r: StepsRecord): Boolean {
        val origin = r.metadata.dataOrigin.packageName
        if (origin in WHOOP_ORIGINS) return false
        return r.metadata.device?.type == Device.TYPE_PHONE || origin == SYSTEM_ORIGIN
    }

    companion object {
        val PERMISSIONS: Set<String> = setOf(HealthPermission.getReadPermission(StepsRecord::class))
        private const val PAGE = 5_000
        private const val SYSTEM_ORIGIN = "android"
        private val WHOOP_ORIGINS = setOf("com.whoop.android")
    }
}

/**
 * Phone steps for «Сегодня» and the header ring (D-029): today's count, the personal goal from
 * the last 14 days (once a day) and the steps inside the planned walk (A35 §4). Single-flight.
 */
class StepsRepository(
    private val source: PhoneStepsSource,
    private val clock: Clock,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow<PhoneSteps>(PhoneSteps.NeedsPermission)
    val state: StateFlow<PhoneSteps> = _state.asStateFlow()

    private var goalFor: Pair<LocalDate, Int>? = null

    /** The permission request for the in-place ask (`ActivityResultLauncher<Set<String>>`). */
    fun permissionContract(): ActivityResultContract<Set<String>, Set<String>> =
        PermissionController.createRequestPermissionResultContract()

    val permissions: Set<String> get() = HealthConnectSteps.PERMISSIONS

    /** Re-reads today's steps; [walk] is the planned walk, if any, to confirm it by steps. */
    suspend fun refresh(walk: WalkWindow? = null) = mutex.withLock {
        _state.value = runCatching { read(walk) }.getOrElse { PhoneSteps.Unavailable }
    }

    private suspend fun read(walk: WalkWindow?): PhoneSteps {
        if (!source.available()) return PhoneSteps.Unavailable
        if (!source.granted()) return PhoneSteps.NeedsPermission
        val zone: ZoneId = clock.zone
        val today = LocalDate.now(clock)
        val now = clock.instant()
        val todayChunks = StepMath.onlyOrigin(source.read(today.atStartOfDay(zone).toInstant(), now))
        val goal = goalFor?.takeIf { it.first == today }?.second ?: run {
            val from = today.minusDays(HISTORY_DAYS).atStartOfDay(zone).toInstant()
            val days = StepMath.daily(StepMath.onlyOrigin(source.read(from, today.atStartOfDay(zone).toInstant())), zone)
            Steps.goal(days.filterKeys { it < today }.values.toList()).also { goalFor = today to it }
        }
        val total = todayChunks.sumOf { it.count }.toInt()
        val inSlot = walk?.let { w -> StepMath.bestWindow(todayChunks, w.from, minOf(w.to, now), w.minutes) }
        return PhoneSteps.Ready(total, goal, inSlot)
    }

    private companion object {
        const val HISTORY_DAYS = 14L
    }
}

@Module
@InstallIn(SingletonComponent::class)
object HealthConnectModule {
    @Provides
    @Singleton
    fun provideSteps(@ApplicationContext context: Context, clock: Clock): StepsRepository =
        StepsRepository(HealthConnectSteps(context), clock)
}
