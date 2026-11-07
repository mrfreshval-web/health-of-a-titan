package com.healthofatitan.app.integration.whoop.day

import com.healthofatitan.app.domain.engine.DayFlag
import com.healthofatitan.app.domain.engine.Regime
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** «Рассвет»: the library format of Agent 16, the rotation of STYLE.md §6 and when the dawn shows. */
class DawnTest {

    /** The shape `contentCompile` produces from content/haiku/library.yaml. */
    private val compiled = """
        {"version":1,"reviewedBy":[],"status":"draft","images":{"dawn":"рассвет","sea":"море"},
         "haiku":[
          {"id":"hk-razgon-001","mode":"razgon","tags":["sleep_good"],"image":"dawn","text":"Сон был глубоким.\nСвет идёт по ступеням —\nиди с ним рядом."},
          {"id":"hk-ravno-001","mode":"ravno","tags":["any"],"image":"sea","text":"a\nb\nc"},
          {"id":"hk-ravno-002","mode":"ravno","tags":["any"],"image":"sea","text":"old\nretired\nhaiku","retired":true},
          {"id":"hk-other","mode":"unknown","tags":["any"],"image":"sea","text":"x\ny\nz"}
         ]}
    """.trimIndent()

    private val tuesday = LocalDate.of(2026, 11, 10)

    private fun h(id: String, mode: Regime, image: String, vararg tags: String, text: String = "$id one\n$id two\n$id three") =
        Haiku(id, mode, tags.toSet().ifEmpty { setOf("any") }, image, text)

    @Test
    fun `library json keeps modes and drops retired and unknown`() {
        val lib = HaikuLibrary.parse(compiled)
        assertEquals(listOf("hk-razgon-001", "hk-ravno-001"), lib.map { it.id })
        assertEquals(Regime.BOOST, lib[0].mode)
        assertEquals(3, lib[0].lines.size)
        assertEquals("иди с ним рядом.", lib[0].lines[2])
    }

    @Test
    fun `day tags follow A09 thresholds, weekday and season`() {
        val tags = HaikuPicker.dayTags(DawnFacts(tuesday, Regime.STEADY, sleepHours = 5.5, orsZ = -1.2, lrb = 1.3))
        assertEquals(setOf("sleep_short", "recovery_low", "strain_high", "workday", "season_autumn", "any"), tags)
        val sunday = HaikuPicker.dayTags(DawnFacts(LocalDate.of(2026, 1, 4), Regime.BOOST, sleepHours = 7.5, orsZ = 0.6))
        assertTrue("weekend" in sunday && "season_winter" in sunday && "sleep_good" in sunday && "recovery_high" in sunday)
        assertEquals("season_summer", HaikuPicker.season(LocalDate.of(2026, 1, 4), southern = true))
    }

    @Test
    fun `mode, truth of tags and the most specific first`() {
        val lib = listOf(
            h("a", Regime.STEADY, "sea"),
            h("b", Regime.STEADY, "dew", "sleep_short"),
            h("c", Regime.STEADY, "fog", "sleep_good"),
            h("d", Regime.BOOST, "sail"),
        )
        val short = DawnFacts(tuesday, Regime.STEADY, sleepHours = 5.0)
        assertEquals("b", HaikuPicker.pick(lib, short, emptyList(), "local")?.id)
        val good = DawnFacts(tuesday, Regime.STEADY, sleepHours = 7.4)
        assertEquals("c", HaikuPicker.pick(lib, good, emptyList(), "local")?.id)
        assertEquals("d", HaikuPicker.pick(lib, DawnFacts(tuesday, Regime.BOOST), emptyList(), "local")?.id)
    }

    @Test
    fun `never repeats an id or a text, and an image rests 7 days`() {
        val lib = listOf(
            h("a", Regime.STEADY, "sea", text = "Ёлка\nв\nснегу"),
            h("b", Regime.STEADY, "sea"),
            h("c", Regime.STEADY, "fog"),
            h("d", Regime.STEADY, "dew", text = "елка в\nснегу\n"),
        )
        val facts = DawnFacts(tuesday, Regime.STEADY)
        val history = listOf(
            ShownHaiku(tuesday.minusDays(3), "a", HaikuPicker.textKey("Ёлка\nв\nснегу"), "sea"),
            ShownHaiku(tuesday.minusDays(9), "c", HaikuPicker.textKey("c one\nc two\nc three"), "fog"),
        )
        // a: shown; b: «sea» shown 3 days ago; c: shown; d: the same text as a (ё → е, punctuation)
        assertNull(HaikuPicker.pick(lib, facts, history, "local"))
        // 8 days later «sea» may come back
        val later = facts.copy(day = tuesday.plusDays(5))
        assertEquals("b", HaikuPicker.pick(lib, later, history, "local")?.id)
    }

    @Test
    fun `no haiku under crisis signals, sick and no data narrow the choice`() {
        val lib = listOf(
            h("gentle-any", Regime.GENTLE, "tea"),
            h("gentle-bow", Regime.GENTLE, "bow"),
            h("gentle-low", Regime.GENTLE, "rain", "recovery_low"),
            h("steady-any", Regime.STEADY, "sea"),
            h("steady-short", Regime.STEADY, "dew", "sleep_short"),
        )
        assertNull(HaikuPicker.pick(lib, DawnFacts(tuesday, Regime.GENTLE, crisis = true), emptyList(), "local"))
        val sick = DawnFacts(tuesday, Regime.STEADY, flags = setOf(DayFlag.SICK), orsZ = -1.5)
        repeat(5) { i ->
            assertEquals("gentle-any", HaikuPicker.pick(lib, sick.copy(day = tuesday.plusDays(i * 10L)), emptyList(), "u$i")?.id)
        }
        val noData = DawnFacts(tuesday, Regime.GENTLE, flags = setOf(DayFlag.CALIBRATION), sleepHours = 5.0)
        assertEquals("steady-any", HaikuPicker.pick(lib, noData, emptyList(), "local")?.id)
    }

    @Test
    fun `ties are seeded by the date and the user`() {
        val lib = (1..20).map { h("t$it", Regime.STEADY, "img$it") }
        val facts = DawnFacts(tuesday, Regime.STEADY)
        val one = HaikuPicker.pick(lib, facts, emptyList(), "local")
        assertEquals(one, HaikuPicker.pick(lib, facts, emptyList(), "local"))
        val days = (0L until 10L).map { HaikuPicker.pick(lib, facts.copy(day = tuesday.plusDays(it)), emptyList(), "local")?.id }
        assertTrue(days.toSet().size > 1)
    }

    @Test
    fun `dawn is due once a day after 5 when today's plan is ready`() {
        val morning = LocalDateTime.of(2026, 11, 10, 6, 52)
        assertTrue(DawnPolicy.due(morning, tuesday, shownToday = false))
        assertFalse(DawnPolicy.due(morning, tuesday, shownToday = true))
        assertFalse(DawnPolicy.due(morning.withHour(4), tuesday, shownToday = false))
        assertFalse(DawnPolicy.due(morning, tuesday.minusDays(1), shownToday = false))
        assertFalse(DawnPolicy.due(morning, null, shownToday = false))
    }

    @Test
    fun `the sun stands as high as the mode and the charge`() {
        val boost = DawnPolicy.sunHeight(Regime.BOOST, 78.0)
        val steady = DawnPolicy.sunHeight(Regime.STEADY, 62.0)
        val gentle = DawnPolicy.sunHeight(Regime.GENTLE, 31.0)
        assertTrue(boost > steady && steady > gentle)
        assertNotEquals(DawnPolicy.sunHeight(Regime.STEADY, 30.0), DawnPolicy.sunHeight(Regime.STEADY, 70.0))
        assertNotNull(DawnPolicy.sunHeight(Regime.GENTLE, null))
    }
}
