package com.healthofatitan.app.feature.training

import com.healthofatitan.app.core.model.profile.ProfileDictionary
import com.healthofatitan.app.core.model.profile.UserConstraints
import com.healthofatitan.app.domain.engine.ItemKind
import com.healthofatitan.app.domain.engine.ItemStatus
import com.healthofatitan.app.domain.engine.Regime
import com.healthofatitan.app.domain.safety.JournalEvent
import com.healthofatitan.app.domain.safety.SafetyEvents
import com.healthofatitan.app.domain.safety.SafetyProjector
import com.healthofatitan.app.domain.safety.SafetyState
import com.healthofatitan.app.domain.training.HiddenReason
import com.healthofatitan.app.domain.training.SoundLevel
import com.healthofatitan.app.domain.training.TrainingDecision
import com.healthofatitan.app.domain.training.TrainingEngine
import com.healthofatitan.app.domain.training.TrainingFlag
import com.healthofatitan.app.domain.training.TrainingLibrary
import com.healthofatitan.app.domain.training.TrainingPlace
import com.healthofatitan.app.integration.whoop.day.DayItemView
import com.healthofatitan.app.integration.whoop.day.DayView
import org.junit.Test
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import java.io.File
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Slice S1 (audit 08.11 Р-1, вето Агента 3): what a person marks in «Я › Еда и тело» and what the
 * safety bus holds reach the training of the day — against the real `content/profile` and
 * `content/training`.
 */
class TrainingSafetyTest {

    @Suppress("UNCHECKED_CAST")
    private fun yaml(path: String): Map<String, Any?> {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "content/training").isDirectory) dir = dir.parentFile
        val file = File(requireNotNull(dir) { "content/ not found" }, "content/$path")
        return Yaml(SafeConstructor(LoaderOptions())).load<Any?>(file.readText()) as Map<String, Any?>
    }

    private val dict = ProfileDictionary.parse(yaml("profile/constraints.yaml"))
    private val lib = TrainingLibrary.fromTrees(
        yaml("training/exercises.yaml"),
        yaml("training/templates.yaml"),
        yaml("training/rules.yaml"),
    )
    private val today = LocalDate.of(2026, 11, 10)

    /** «Силовая · 45 мин» in Разгон: the strongest session the day can ask for. */
    private val strength = DayView(
        dayKey = today,
        regime = Regime.BOOST,
        flags = emptySet(),
        headline = "",
        summary = "",
        fired = emptyList(),
        items = listOf(DayItemView("training", ItemKind.TRAINING, "Силовая · 45 мин", null, 18 * 60, 18 * 60 + 45, ItemStatus.OPEN)),
        bedtimeMin = 23 * 60,
        caffeineCutoffMin = 14 * 60,
        windDownMin = 22 * 60,
    )

    private fun decide(c: UserConstraints, state: SafetyState = SafetyState.NORMAL, place: TrainingPlace = TrainingPlace.GYM): TrainingDecision {
        val guard = TrainingSafety.guard(c, dict, state, lib.rules)
        return TrainingRequests.day(TrainingEngine(lib), strength, today, place, SoundLevel.DEFAULT, guard).decision
    }

    private fun planned(c: UserConstraints, state: SafetyState = SafetyState.NORMAL, place: TrainingPlace = TrainingPlace.GYM) =
        assertIs<TrainingDecision.Planned>(decide(c, state, place)).session

    @Test
    fun `an empty profile and a calm bus change nothing`() {
        val guard = TrainingSafety.guard(UserConstraints.NONE, dict, SafetyState.NORMAL, lib.rules)
        // Only the tier gate of A40 §3 (no tap for Экстрим) stays: it never touches a Classic session.
        assertEquals(setOf("extreme_not_consented"), guard.codes)
        assertTrue(guard.flags.isEmpty())
        val engine = TrainingEngine(lib)
        assertEquals(
            TrainingRequests.day(engine, strength, today, TrainingPlace.GYM, SoundLevel.DEFAULT).decision,
            TrainingRequests.day(engine, strength, today, TrainingPlace.GYM, SoundLevel.DEFAULT, guard).decision,
        )
    }

    @Test
    fun `a knee injury takes jumps and deep knee bends out of the session`() {
        val guard = TrainingSafety.guard(UserConstraints(injuries = setOf("knee_injury")), dict, SafetyState.NORMAL, lib.rules)
        assertTrue("knee_pain" in guard.codes, "${guard.codes}")
        assertTrue("no_impact" in guard.codes, "${guard.codes}")
        for (place in TrainingPlace.entries) {
            val s = planned(UserConstraints(injuries = setOf("knee_injury")), place = place)
            val items = s.blocks.flatMap { it.items }
            assertTrue(items.none { "high_impact" in it.exercise.riskTags }, "$place: ${items.map { it.exercise.id }}")
            assertTrue(items.none { "deep_knee_flexion" in it.exercise.riskTags }, "$place: ${items.map { it.exercise.id }}")
            assertTrue(items.none { it.exercise.pattern == "jump" }, "$place: ${items.map { it.exercise.id }}")
        }
    }

    @Test
    fun `pregnancy and the first six months after birth hide the program`() {
        for (stage in listOf("pregnancy", "postpartum_6m")) {
            val d = assertIs<TrainingDecision.Hidden>(decide(UserConstraints(lifeStage = setOf(stage))), stage)
            assertEquals(HiddenReason.NO_PROGRAM, d.reason, stage)
        }
    }

    @Test
    fun `lactation and blood pressure medicines give the gentle Classic`() {
        for (c in listOf(UserConstraints(lifeStage = setOf("lactation")), UserConstraints(medical = setOf("heart_bp_meds")))) {
            val guard = TrainingSafety.guard(c, dict, SafetyState.NORMAL, lib.rules)
            assertTrue(TrainingSafety.GENTLE_CODE in guard.codes, "$c: ${guard.codes}")
            val s = planned(c)
            assertTrue((s.rpeMax ?: 10) <= 6, "$c: RPE ${s.rpeMax}")
            assertTrue(s.blocks.flatMap { it.items }.none { "valsalva" in it.exercise.riskTags || "heavy_load" in it.exercise.riskTags }, "$c")
        }
    }

    @Test
    fun `an eating disorder history closes extra sessions and chippers`() {
        val guard = TrainingSafety.guard(UserConstraints(medical = setOf("ed_history")), dict, SafetyState.NORMAL, lib.rules)
        assertTrue(TrainingFlag.ED_CARE in guard.flags)
    }

    @Test
    fun `a crisis on the bus hides the training, sickness leaves mobility`() {
        val crisis = SafetyProjector.project(listOf(JournalEvent(1, SafetyEvents.CRISIS, "3", 1_000L)), 2_000L)
        assertEquals(HiddenReason.EMERGENCY, assertIs<TrainingDecision.Hidden>(decide(UserConstraints.NONE, crisis)).reason)
        assertEquals(setOf(TrainingFlag.SICK), TrainingSafety.bus(setOf(com.healthofatitan.app.domain.safety.Restriction.NO_LOAD)).flags)
    }

    @Test
    fun `without the dictionary a non-empty profile is read the safe way`() {
        val p = TrainingSafety.profile(UserConstraints(injuries = setOf("knee_injury")), null)
        assertTrue(TrainingSafety.GENTLE_CODE in p.codes(lib.rules))
    }
}
