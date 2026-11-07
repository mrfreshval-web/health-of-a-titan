package com.healthofatitan.app.domain.training

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrainingLibraryTest {

    private val lib = Content.library

    @Test
    fun `the whole library parses`() {
        assertEquals(74, lib.exercises.size)
        assertEquals(16, lib.templates.size)
        assertEquals(45, lib.exercises.values.count { it.level == Tier.CLASSIC })
        val squat = assertNotNull(lib.exercise("ex-squat-bw"))
        assertEquals("squat", squat.pattern)
        assertTrue("deep_knee_flexion" in squat.riskTags)
        assertEquals("12-15", squat.default.reps)
        assertEquals(listOf("stand", "bottom"), squat.animation.poses.map { it.key })
    }

    @Test
    fun `every template item points to an exercise`() {
        for (t in lib.templates.values) {
            for (b in t.blocks) for (i in b.items) {
                assertNotNull(lib.exercise(i.ex), "${t.id}: ${i.ex}")
                i.alt.forEach { assertNotNull(lib.exercise(it), "${t.id}: alt $it") }
            }
        }
    }

    @Test
    fun `modes and lighter are read`() {
        val f = assertNotNull(lib.template("tpl-cl-functional-home"))
        assertEquals(ModeUse.Full, f.modes[DayMode.BOOST])
        assertEquals(ModeUse.Lighter, f.modes[DayMode.STEADY])
        assertEquals(ModeUse.Swap("tpl-mob-recovery"), f.modes[DayMode.GENTLE])
        assertEquals(-1, f.lighter.setsDelta)
        assertEquals(6, f.lighter.rpeMax)
        assertEquals("tpl-cl-functional-home@1", f.ref)
        val db = assertNotNull(lib.template("tpl-cl-db-strength"))
        assertEquals(setOf(BlockType.FINISHER), db.lighter.dropBlocks)
    }

    @Test
    fun `rules are read`() {
        val r = lib.rules
        assertNull(r.regimeCaps.getValue(DayMode.BOOST).allowMethods)
        assertEquals(8, r.regimeCaps.getValue(DayMode.BOOST).rpeMax[Tier.CLASSIC])
        assertEquals(setOf("zone2", "mobility", "stretching"), r.regimeCaps.getValue(DayMode.GENTLE).allowMethods)
        assertEquals("Z2", r.regimeCaps.getValue(DayMode.GENTLE).zoneMax)
        assertTrue("deep_knee_flexion" in r.profileCodes.getValue("knee_injury").forbidTags)
        assertTrue(r.profileCodes.getValue("pregnancy").noProgram)
        assertTrue(r.profileCodes.getValue("chest_pain_exertion").hideTraining)
        assertEquals(Tier.ADVANCED, r.profileCodes.getValue("extreme_not_consented").maxLevel)
        assertEquals(90, r.player.restByCategory["strength"])
        assertEquals(3, r.player.countdownS)
        assertTrue("лечит" in r.stopwords)
    }

    @Test
    fun `the JSON of the content pipeline gives the same library`() {
        val fromJson = TrainingLibrary.fromJson(
            Content.json(Content.exercises),
            Content.json(Content.templates),
            Content.json(Content.rules),
            Content.json(Content.poses),
        )
        assertEquals(lib.exercises, fromJson.exercises)
        assertEquals(lib.templates, fromJson.templates)
        assertEquals(lib.rules, fromJson.rules)
        assertEquals(lib.poses.exerciseIds, fromJson.poses.exerciseIds)
        assertEquals(lib.poses.poses("ex-squat-bw"), fromJson.poses.poses("ex-squat-bw"))
    }

    @Test
    fun `reviewed only when every file is signed`() {
        // `reviewedBy: []` until Agents 3 and 10 sign: the app then marks texts as a draft.
        val signed = listOf(Content.exercises, Content.templates, Content.rules).all { (it["reviewedBy"] as? List<*>).orEmpty().isNotEmpty() }
        assertEquals(signed, lib.reviewed)
    }
}
