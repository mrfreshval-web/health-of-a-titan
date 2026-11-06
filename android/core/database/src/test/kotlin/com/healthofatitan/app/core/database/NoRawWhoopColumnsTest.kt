package com.healthofatitan.app.core.database

import org.junit.Test
import java.io.File
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * D-009 / ADR-02 / A10 G2 criterion 2: raw WHOOP data is never persisted. The exported Room
 * schema (and the entity classes, as a second layer) must not contain raw WHOOP fields.
 *
 * Names are compared as tokens (snake_case and camelCase split), so `strain` is caught in
 * `day_strain` / `dayStrain` but `constraint` is not a false positive.
 */
class NoRawWhoopColumnsTest {

    /** Token sequences of WHOOP API v2 raw fields (cycle, recovery, sleep, workout). */
    private val forbidden: List<List<String>> = listOf(
        "recovery_score", "strain", "spo2", "spo2_percentage", "skin_temp", "skin_temp_celsius",
        "hrv", "rmssd", "hrv_rmssd_milli", "resting_heart_rate", "kilojoule", "average_heart_rate",
        "max_heart_rate", "respiratory_rate", "sleep_performance_percentage",
        "sleep_consistency_percentage", "sleep_efficiency_percentage", "zone_durations",
        "zone_duration", "total_in_bed_time_milli", "total_awake_time_milli",
        "total_light_sleep_time_milli", "total_slow_wave_sleep_time_milli",
        "total_rem_sleep_time_milli", "disturbance_count", "sleep_cycle_count", "percent_recorded",
        // Activity and heart rate (D-024, A18 И3b.2): steps, HR zones, any heart-rate series.
        "step_count", "steps", "zone_zero_milli", "zone_one_milli", "zone_two_milli", "zone_three_milli",
        "zone_four_milli", "zone_five_milli", "heart_rate", "hr_bpm", "bpm", "max_hr", "avg_hr", "average_hr",
        "resting_hr", "distance_meter", "altitude_gain_meter", "altitude_change_meter",
        // Sleep need and calibration internals of WHOOP.
        "sleep_needed", "baseline_milli", "need_from_sleep_debt_milli", "need_from_recent_strain_milli",
        "need_from_recent_nap_milli", "user_calibrating", "total_no_data_time_milli",
    ).map { it.split('_') }.distinct()

    private fun tokens(name: String): List<String> =
        name.replace(Regex("([a-z0-9])([A-Z])"), "$1_$2").lowercase().split('_', '-', '.').filter { it.isNotEmpty() }

    private fun violates(name: String): List<String> {
        val t = tokens(name)
        val hits = forbidden.filter { f -> t.windowed(f.size).any { it == f } }.map { it.joinToString("_") }
        // `zone_*`: any name that starts with «zone» is a heart-rate zone of WHOOP (time zones
        // are stored as `tz_offset_*`, never as `zone`).
        return if (t.firstOrNull() == "zone") hits + "zone_*" else hits
    }

    private fun schemaFiles(): List<File> {
        val roots = listOfNotNull(System.getProperty("hot.room.schemaDir"), System.getProperty("hot.room.buildDir"))
            .map(::File)
        return roots.flatMap { root ->
            if (!root.exists()) emptyList()
            else root.walkTopDown()
                .filter { it.isFile && it.extension == "json" && it.path.contains(HotDatabase::class.java.name) }
                .toList()
        }
    }

    @Test
    fun `tokenizer catches raw fields and ignores look-alikes`() {
        assertTrue(violates("day_strain").isNotEmpty())
        assertTrue(violates("recoveryScore").isNotEmpty())
        assertTrue(violates("hrv_rmssd_milli").isNotEmpty())
        assertTrue(violates("constraint").isEmpty())
        assertTrue(violates("constraints_json").isEmpty())
        assertTrue(violates("step_count").isNotEmpty())
        assertTrue(violates("dailySteps").isNotEmpty())
        assertTrue(violates("zone_two_milli").isNotEmpty())
        assertTrue(violates("zoneMinutes").isNotEmpty())
        assertTrue(violates("avg_heart_rate").isNotEmpty())
        assertTrue(violates("maxHr").isNotEmpty())
        assertTrue(violates("restingHeartRate").isNotEmpty())
        // HoT-derived columns of slice 1 stay allowed.
        listOf("metric_daily", "baseline_snapshot", "median28", "mad28", "ewma7", "baseline_nights", "tz_offset_min", "coverage")
            .forEach { assertTrue(violates(it).isEmpty(), it) }
    }

    @Test
    fun `exported room schema has no raw WHOOP columns`() {
        val files = schemaFiles()
        if (files.isEmpty()) {
            fail(
                "No exported Room schema for ${HotDatabase::class.java.name} found in " +
                    "${System.getProperty("hot.room.schemaDir")} or build dir. exportSchema must stay true.",
            )
        }
        val names = Regex("\"(?:tableName|fieldPath|columnName)\"\\s*:\\s*\"([^\"]+)\"")
        val problems = files.flatMap { file ->
            names.findAll(file.readText()).map { it.groupValues[1] }.flatMap { name ->
                violates(name).map { "${file.name}: '$name' matches raw WHOOP field '$it'" }
            }
        }
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }

    @Test
    fun `entity classes have no raw WHOOP fields`() {
        val problems = HotDatabase.ENTITIES.flatMap { kClass ->
            val cls = kClass.java
            (listOf(cls.simpleName) + cls.declaredFields.map { it.name }).flatMap { name ->
                violates(name).map { "${cls.simpleName}.$name matches raw WHOOP field '$it'" }
            }
        }
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }
}
