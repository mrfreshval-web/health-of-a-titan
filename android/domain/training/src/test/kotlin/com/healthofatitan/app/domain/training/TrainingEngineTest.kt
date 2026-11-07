package com.healthofatitan.app.domain.training

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class TrainingEngineTest {

    private val lib = Content.library
    private val engine = TrainingEngine(lib)

    private fun planned(r: TrainingRequest): TrainingSession {
        val d = engine.plan(r)
        return assertIs<TrainingDecision.Planned>(d, "expected a session for $r, got $d").session
    }

    private val profiles: List<Set<String>> = listOf(
        emptySet(),
        setOf("knee_injury"),
        setOf("knee_pain", "lower_back_pain"),
        setOf("shoulder_injury", "wrist_pain"),
        setOf("hypertension", "screening_not_cleared"),
        setOf("no_floor_work", "no_impact", "no_deep_knee_flexion"),
        setOf("ankle_injury", "hip_pain", "neck_pain"),
        setOf("heart_condition", "fainting_dizziness", "hernia", "eye_pressure"),
    )

    /** M-ALL (A40 §2.4), the profile and the day ceiling hold for every combination. */
    @Test
    fun `every day, place, kind and profile keeps warm-up, stretch and the bans`() {
        var checked = 0
        for (mode in DayMode.entries) for (kind in listOf(null) + DayTrainingKind.entries) for (place in TrainingPlace.entries) for (codes in profiles) {
            val r = TrainingRequest(mode = mode, kind = kind, place = place, codes = codes)
            val s = planned(r)
            val where = "$mode/$kind/$place/$codes → ${s.ref}"
            val first = s.blocks.first()
            val last = s.blocks.last()
            assertEquals(BlockType.WARMUP, first.type, where)
            assertTrue(first.items.any { it.exercise.isMobility }, "$where: warm-up without mobility")
            assertEquals(BlockType.COOLDOWN, last.type, where)
            assertTrue(last.items.any { it.exercise.isStretch }, "$where: cool-down without a stretch")
            val end = last.items.last().exercise
            assertTrue(end.isStretch || end.isBreathing, "$where: cool-down ends with ${end.id}")

            val cap = lib.rules.regimeCaps.getValue(s.mode)
            val forbidden = cap.forbidTags + codes.flatMap { lib.rules.profileCodes[it]?.forbidTags.orEmpty() }
            val patterns = codes.flatMap { lib.rules.profileCodes[it]?.forbidPatterns.orEmpty() }.toSet()
            for (i in s.items) {
                val e = i.exercise
                assertTrue(e.riskTags.none { it in forbidden }, "$where: ${e.id} has ${e.riskTags intersect forbidden}")
                assertTrue(e.pattern !in patterns, "$where: ${e.id} pattern ${e.pattern}")
                assertTrue(e.contraindications.none { it in codes }, "$where: ${e.id} contraindicated")
                assertEquals(Tier.CLASSIC, e.level, "$where: ${e.id} above Classic")
                assertTrue(i.sets >= 1)
                assertTrue(i.reps != null || i.timeS != null || i.distanceM != null, "$where: ${e.id} has no dose")
            }
            val rpe = assertNotNull(s.rpeMax, where)
            assertTrue(rpe <= cap.rpeMax.getValue(Tier.CLASSIC), "$where: RPE $rpe over the day cap")
            if (s.mode == DayMode.GENTLE) assertTrue(s.template.method in setOf("zone2", "mobility", "stretching"), where)
            assertTrue(s.minutes > 0, where)
            checked++
        }
        assertTrue(checked > 500)
    }

    @Test
    fun `same input gives the same session`() {
        val r = TrainingRequest(DayMode.STEADY, DayTrainingKind.STRENGTH, codes = setOf("knee_pain"))
        assertEquals(engine.plan(r), engine.plan(r))
    }

    @Test
    fun `Разгон runs the full functional base`() {
        val s = planned(TrainingRequest(DayMode.BOOST, DayTrainingKind.STRENGTH, place = TrainingPlace.HOME))
        assertEquals("tpl-cl-functional-home", s.template.id)
        assertEquals(false, s.lighter)
        val main = s.blocks.single { it.type == BlockType.MAIN }
        assertEquals(3, main.rounds)
        assertEquals(listOf("ex-squat-bw", "ex-pushup", "ex-reverse-lunge", "ex-glute-bridge", "ex-plank"), main.items.map { it.exercise.id })
        assertEquals(7, s.rpeMax) // day cap 8 for Classic in Разгон, template intensity 6-7 → 7
        assertEquals(30, s.minutes)
    }

    @Test
    fun `Ровно takes the lighter version, warm-up and cool-down untouched`() {
        val full = planned(TrainingRequest(DayMode.BOOST, DayTrainingKind.STRENGTH))
        val s = planned(TrainingRequest(DayMode.STEADY, DayTrainingKind.STRENGTH))
        assertEquals("tpl-cl-functional-home", s.template.id)
        assertTrue(s.lighter)
        assertEquals(2, s.blocks.single { it.type == BlockType.MAIN }.rounds)
        assertEquals(6, s.rpeMax)
        assertTrue(s.minutes < full.minutes)
        assertEquals(full.blocks.first(), s.blocks.first())
        assertEquals(full.blocks.last(), s.blocks.last())
    }

    @Test
    fun `Бережно swaps strength for recovery`() {
        val d = engine.plan(TrainingRequest(DayMode.GENTLE, DayTrainingKind.STRENGTH))
        val s = assertIs<TrainingDecision.Planned>(d).session
        assertEquals("tpl-mob-recovery", s.template.id)
        assertEquals("tpl-cl-functional-home", s.chosenTemplateId)
        assertTrue(s.swapped)
        assertEquals("Z2", s.zoneMax)
        assertTrue(d.fired.any { it.id == "MODE" && "Восстановление" in it.why })
    }

    @Test
    fun `dumbbells at home and the gym pick their templates`() {
        assertEquals("tpl-cl-db-strength", planned(TrainingRequest(DayMode.BOOST, DayTrainingKind.STRENGTH, place = TrainingPlace.HOME, equipment = TrainingPlace.HOME.equipment + "dumbbells")).template.id)
        assertEquals("tpl-cl-gym-circuit", planned(TrainingRequest(DayMode.BOOST, DayTrainingKind.STRENGTH, place = TrainingPlace.GYM, minutes = 45)).template.id)
        val lighterDb = planned(TrainingRequest(DayMode.STEADY, DayTrainingKind.STRENGTH, equipment = TrainingPlace.HOME.equipment + "dumbbells"))
        assertTrue(lighterDb.blocks.none { it.type == BlockType.FINISHER }, "lighter drops the finisher")
        assertTrue(lighterDb.blocks.single { it.type == BlockType.MAIN }.items.all { it.sets == 2 })
    }

    @Test
    fun `Classic has no intervals, they become Zone 2`() {
        val d = engine.plan(TrainingRequest(DayMode.BOOST, DayTrainingKind.INTERVALS, place = TrainingPlace.OUTDOOR))
        assertEquals("tpl-cl-zone2", assertIs<TrainingDecision.Planned>(d).session.template.id)
        assertTrue(d.fired.any { it.id == "CL-INT" })
    }

    @Test
    fun `Zone 2 in Бережно is a shorter walk in Z1`() {
        val full = planned(TrainingRequest(DayMode.STEADY, DayTrainingKind.ZONE2, place = TrainingPlace.OUTDOOR))
        assertEquals(1800, full.blocks.single { it.type == BlockType.MAIN }.items.single().timeS)
        val s = planned(TrainingRequest(DayMode.GENTLE, DayTrainingKind.MOBILITY, place = TrainingPlace.OUTDOOR))
        assertEquals("tpl-mob-recovery", s.template.id)
    }

    @Test
    fun `illness keeps only recovery up to 20 minutes in Z1`() {
        val d = engine.plan(TrainingRequest(DayMode.BOOST, DayTrainingKind.STRENGTH, flags = setOf(TrainingFlag.SICK)))
        val s = assertIs<TrainingDecision.Planned>(d).session
        assertEquals("tpl-mob-recovery", s.template.id)
        assertTrue(s.minutes <= 20, "${s.minutes}")
        assertEquals("Z1", s.zoneMax)
        assertEquals("TG-02", d.fired.first().id)
    }

    @Test
    fun `emergency, stop signals, chest pain and pregnancy hide the training`() {
        assertEquals(HiddenReason.EMERGENCY, assertIs<TrainingDecision.Hidden>(engine.plan(TrainingRequest(DayMode.BOOST, flags = setOf(TrainingFlag.EMERGENCY)))).reason)
        assertEquals(HiddenReason.STOP_SIGNAL, assertIs<TrainingDecision.Hidden>(engine.plan(TrainingRequest(DayMode.BOOST, flags = setOf(TrainingFlag.STOP_SIGNAL)))).reason)
        assertEquals(HiddenReason.DOCTOR, assertIs<TrainingDecision.Hidden>(engine.plan(TrainingRequest(DayMode.BOOST, codes = setOf("chest_pain_exertion")))).reason)
        assertEquals(HiddenReason.NO_PROGRAM, assertIs<TrainingDecision.Hidden>(engine.plan(TrainingRequest(DayMode.BOOST, codes = setOf("pregnancy")))).reason)
    }

    @Test
    fun `knee injury replaces squats and lunges, never keeps them`() {
        val d = engine.plan(TrainingRequest(DayMode.BOOST, DayTrainingKind.STRENGTH, codes = setOf("knee_injury")))
        val s = assertIs<TrainingDecision.Planned>(d).session
        val ids = s.items.map { it.exercise.id }
        assertTrue("ex-squat-bw" !in ids && "ex-reverse-lunge" !in ids, "$ids")
        assertTrue(s.items.none { it.exercise.pattern == "lunge" })
        assertTrue(d.fired.any { it.id == "PROFILE" })
        // the stretch of the cool-down survives even though some stretches load the knee
        assertTrue(s.blocks.last().items.any { it.exercise.isStretch })
    }

    @Test
    fun `wrist pain swaps push-ups for a press without wrist load`() {
        val s = planned(TrainingRequest(DayMode.BOOST, DayTrainingKind.STRENGTH, codes = setOf("wrist_pain")))
        val main = s.blocks.single { it.type == BlockType.MAIN }
        assertTrue(main.items.none { "wrist_load" in it.exercise.riskTags })
        val replaced = main.items.filter { it.replaced != null }
        replaced.forEach { assertNotNull(it.reps ?: it.timeS ?: it.distanceM?.toString()) }
    }

    @Test
    fun `screening not cleared caps RPE at 6`() {
        val d = engine.plan(TrainingRequest(DayMode.BOOST, DayTrainingKind.STRENGTH, codes = setOf("screening_not_cleared")))
        assertEquals(6, assertIs<TrainingDecision.Planned>(d).session.rpeMax)
        assertTrue(d.fired.any { it.id == "TG-04" })
    }

    @Test
    fun `light week and travel never give more than Ровно`() {
        val week = planned(TrainingRequest(DayMode.BOOST, DayTrainingKind.STRENGTH, flags = setOf(TrainingFlag.LIGHT_WEEK)))
        assertEquals(DayMode.STEADY, week.mode)
        assertTrue(week.lighter)
        val travel = planned(TrainingRequest(DayMode.BOOST, DayTrainingKind.STRENGTH, place = TrainingPlace.GYM, flags = setOf(TrainingFlag.TRAVEL)))
        assertEquals(DayMode.STEADY, travel.mode)
        assertEquals("tpl-cl-functional-home", travel.template.id) // hotel: no gym circuit
    }

    @Test
    fun `see a specialist keeps only mobility`() {
        val s = planned(TrainingRequest(DayMode.BOOST, DayTrainingKind.STRENGTH, minutes = 10, flags = setOf(TrainingFlag.SEE_SPECIALIST)))
        assertTrue(s.template.id in TrainingEngine.SPECIALIST_TEMPLATES, s.template.id)
        assertEquals("tpl-mob-morning", s.template.id)
    }

    @Test
    fun `rest days and mobility kinds use the mobility templates`() {
        assertEquals("tpl-mob-morning", planned(TrainingRequest(DayMode.STEADY, DayTrainingKind.MOBILITY, minutes = 10)).template.id)
        assertEquals("tpl-mob-recovery", planned(TrainingRequest(DayMode.STEADY, DayTrainingKind.MOBILITY, minutes = 30)).template.id)
        assertEquals("tpl-mob-morning", planned(TrainingRequest(DayMode.STEADY, DayTrainingKind.REST)).template.id)
    }

    @Test
    fun `profile codes come from the shared profile`() {
        val rules = lib.rules
        assertEquals(setOf("extreme_not_consented"), TrainingProfile().codes(rules))
        val knee = TrainingProfile(injuries = mapOf("knee" to "acute"), extremeConsent = true).codes(rules)
        assertEquals(setOf("knee_injury"), knee)
        val chronic = TrainingProfile(injuries = mapOf("knee" to "chronic_stable"), movementLimits = setOf("no_impact"), extremeConsent = true).codes(rules)
        assertEquals(setOf("knee_pain", "no_impact"), chronic)
        val screening = TrainingProfile(screeningYes = setOf("S1", "S3"), extremeConsent = true).codes(rules)
        assertTrue("screening_not_cleared" in screening && "hypertension" in screening && "fainting_dizziness" in screening, "$screening")
        val cleared = TrainingProfile(screeningYes = setOf("S4"), doctorCleared = true, extremeConsent = true).codes(rules)
        assertTrue("screening_not_cleared" !in cleared)
        val preg = TrainingProfile(lifeStages = setOf("pregnancy"), selfReports = setOf("hernia"), extremeConsent = true).codes(rules)
        assertEquals(setOf("hernia", "pregnancy"), preg)
    }

    @Test
    fun `the why lines carry no red-line words`() {
        val words = lib.rules.stopwords
        val lines = buildList {
            for (mode in DayMode.entries) for (kind in listOf(null) + DayTrainingKind.entries) for (codes in profiles) {
                addAll(engine.plan(TrainingRequest(mode, kind, codes = codes)).fired.map { it.why })
            }
            for (f in TrainingFlag.entries) addAll(engine.plan(TrainingRequest(DayMode.BOOST, flags = setOf(f))).fired.map { it.why })
        }
        for (l in lines.toSet()) {
            val low = l.lowercase()
            words.forEach { w -> if (Regex("(^|[^\\p{L}])" + Regex.escape(w)).containsMatchIn(low)) fail("«$l» has «$w»") }
        }
    }

    @Test
    fun `upper bound of an RPE range`() {
        assertEquals(7, TrainingEngine.upper("6-7"))
        assertEquals(8, TrainingEngine.upper("8"))
        assertNull(TrainingEngine.upper(null))
    }
}
