package com.healthofatitan.app.feature.today

import com.healthofatitan.app.core.model.nav.HotRoute
import com.healthofatitan.app.domain.today.DayMode
import com.healthofatitan.app.domain.today.DayRowId
import com.healthofatitan.app.domain.today.DayRowView
import com.healthofatitan.app.domain.today.DayTap
import com.healthofatitan.app.domain.today.DayTask
import com.healthofatitan.app.domain.today.GoalSource
import com.healthofatitan.app.domain.today.MeView
import com.healthofatitan.app.domain.today.Scenario
import com.healthofatitan.app.domain.today.Sphere
import com.healthofatitan.app.domain.today.StopWords
import com.healthofatitan.app.domain.today.TaskKind
import com.healthofatitan.app.domain.today.TodayCopy
import com.healthofatitan.app.domain.today.WeekGoal
import com.healthofatitan.app.domain.today.WeekPlan
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The pure rules of the top of «Сегодня» by mockup 05b (A66 §1.1, §3.2–3.4, §11.2): the sun of
 * Заряд, the glow of the columns, the words of the tiles, «Сейчас» and its line of the link.
 */
class TodayTopTest {

    private fun near(expected: Float, actual: Float, eps: Float = 0.01f) =
        assertTrue(kotlin.math.abs(expected - actual) < eps, "expected $expected, was $actual")

    @Test
    fun `the sun stands linear in Заряд, the norm 50 in the middle of its travel`() {
        // A66 §3.2 on the screen: y = 452 − 3.02 × Заряд; the scene starts at y 100
        near(437f - 100f, TodayTopRules.sunCenterY(5f), 0.2f)
        near(452f - 3.02f * 50f - 100f, TodayTopRules.sunCenterY(50f))
        near(452f - 3.02f * 95f - 100f, TodayTopRules.sunCenterY(95f))
        val low = TodayTopRules.sunCenterY(5f)
        val high = TodayTopRules.sunCenterY(95f)
        near((low + high) / 2f, TodayTopRules.sunCenterY(50f))
        // linear: the same step of Заряд moves the sun by the same distance above and below the norm
        near(TodayTopRules.sunCenterY(40f) - TodayTopRules.sunCenterY(50f), TodayTopRules.sunCenterY(50f) - TodayTopRules.sunCenterY(60f))
        // outside the scale it stays at its ends
        assertEquals(TodayTopRules.sunCenterY(5f), TodayTopRules.sunCenterY(0f))
        assertEquals(TodayTopRules.sunCenterY(95f), TodayTopRules.sunCenterY(100f))
        assertTrue(TodayTopRules.sunCenterY(95f) > 0f, "the highest sun is still inside the scene")
        assertTrue(TodayTopRules.sunCenterY(5f) < TodayTopRules.feetY, "the lowest sun is above the horizon")
    }

    @Test
    fun `the frame stands as A66 §11_2 says`() {
        near(352f, TodayTopRules.feetY)
        near(352f - 0.875f * 440f, TodayTopRules.frameTop)
        // the axis of the Titan is the centre of the 412 dp screen
        near(206f, TodayTopRules.FRAME_LEFT + TodayTopRules.FRAME / 2f)
    }

    private fun row(fill: Float, counted: Boolean = true) =
        DayRowView(DayRowId.FOOD, "Питание", "◒", "2 из 4 приёмов", null, fill, counted, tap = DayTap.NUTRITION, spoken = "")

    @Test
    fun `the columns glow by the share of the closed day, each row of «День» at most 1`() {
        assertEquals(0f, TodayTopRules.dayShare(emptyList()))
        near(0.5f, TodayTopRules.dayShare(listOf(row(1f), row(0f))))
        near(0.5f, TodayTopRules.dayShare(listOf(row(1.8f), row(0f))), 0.001f)
        // a row without a norm (load without WHOOP, «по самочувствию») is left out of the share
        near(0.5f, TodayTopRules.dayShare(listOf(row(1f), row(0.9f, counted = false), row(0f))))
        assertEquals(TodayTopRules.BASE, TodayTopRules.glowTop(0f))
        assertEquals(TodayTopRules.CAPITAL, TodayTopRules.glowTop(1f))
        near((TodayTopRules.BASE + TodayTopRules.CAPITAL) / 2f, TodayTopRules.glowTop(0.5f))
        assertEquals(TodayTopRules.CAPITAL, TodayTopRules.glowTop(2f))
        assertEquals(40, TodayTopRules.percent(0.4f))
    }

    @Test
    fun `the words compare with the own norm, Бережно softens and the return has no below`() {
        assertEquals(TodayTopCopy.ABOVE, TodayTopRules.chargeWord(68, gentle = false, returning = false))
        assertEquals(TodayTopCopy.ABOVE, TodayTopRules.chargeWord(58, gentle = false, returning = false))
        assertEquals(TodayTopCopy.USUAL, TodayTopRules.chargeWord(57, gentle = false, returning = false))
        assertEquals(TodayTopCopy.USUAL, TodayTopRules.chargeWord(43, gentle = false, returning = false))
        assertEquals(TodayTopCopy.BELOW, TodayTopRules.chargeWord(42, gentle = false, returning = false))
        assertEquals(TodayTopCopy.BELOW_GENTLE, TodayTopRules.chargeWord(24, gentle = true, returning = false))
        assertNull(TodayTopRules.chargeWord(24, gentle = false, returning = true))
        assertEquals(TodayTopCopy.USUAL, TodayTopRules.word(0.2, gentle = false, returning = false))
        assertEquals(TodayTopCopy.ABOVE, TodayTopRules.word(0.5, gentle = false, returning = false))
        assertEquals(TodayTopCopy.BELOW, TodayTopRules.word(-0.5, gentle = false, returning = false))
        assertNull(TodayTopRules.word(null, gentle = false, returning = false))
    }

    private fun me(
        charge: Int? = 68,
        night: String? = "7:12",
        need: String? = "7:40",
        self: Int? = 4,
        mode: DayMode? = DayMode.RAZGON,
        calibration: String? = null,
    ) = MeView(showTitan = true, mode = mode, haiku = null, lead = null, charge = charge, night = night, need = need, self = self, calibration = calibration)

    @Test
    fun `three tiles from what «Я сегодня» shows`() {
        val tiles = TodayTopRules.tiles(me(), Scenario.DAY, nightMinutes = 432, needMinutes = 460, nightZ = 0.1)
        assertEquals(listOf(MetricKind.CHARGE, MetricKind.NIGHT, MetricKind.YOU), tiles.map { it.kind })
        val (charge, night, you) = tiles
        assertEquals("68", charge.shown)
        assertEquals(TodayTopCopy.ABOVE, charge.word)
        near((68 - 5) / 90f, charge.arc!!)
        near((43 - 5) / 90f, charge.band!!.start)
        near((57 - 5) / 90f, charge.band!!.endInclusive)
        assertEquals("Заряд 68, выше обычного", charge.spoken)
        assertEquals("7:12", night.shown)
        assertEquals(TodayTopCopy.USUAL, night.word)
        near(432f / 720f, night.arc!!)
        near((460 - 30) / 720f, night.band!!.start)
        assertEquals("4/5", you.shown)
        near(0.75f, you.arc!!)
        assertEquals(TodayCopy.YOU_CAPTION, you.word)
        assertTrue(tiles.all { it.route == HotRoute.WHY })
    }

    @Test
    fun `without data a tile shows a dash, the dashed arc and an honest word`() {
        val tiles = TodayTopRules.tiles(me(charge = null, night = null, need = null, self = null), Scenario.SELF, null, null, null)
        tiles.forEach {
            assertEquals(TodayTopCopy.NO_VALUE, it.shown)
            assertNull(it.arc, "dashed: ${it.kind}")
            assertNull(it.band)
        }
        assertEquals(TodayTopCopy.NO_DATA, tiles[0].word)
        assertEquals(TodayTopCopy.NO_DATA, tiles[1].word)
        assertEquals(TodayTopCopy.MARK, tiles[2].word)
        assertEquals(HotRoute.CHECKIN, tiles[2].route, "«Ты» without an answer opens «Как ты?»")
        assertEquals("Заряд, нет данных", tiles[0].spoken)

        val pending = TodayTopRules.tiles(me(charge = null, night = null, self = null, mode = null), Scenario.NIGHT_PENDING, null, null, null)
        assertEquals(TodayTopCopy.COUNTING, pending[0].word)
        assertEquals(TodayTopCopy.COUNTING, pending[1].word)

        // calibration: the line under the tiles speaks, the tile keeps quiet
        val calibrating = TodayTopRules.tiles(me(charge = null, calibration = "x"), Scenario.DAY, 432, 460, null)
        assertNull(calibrating[0].word)
        // the night without a known norm says its need instead
        assertEquals(TodayCopy.need("7:40"), calibrating[1].word)
    }

    @Test
    fun `Бережно softens the word of a low night`() {
        val gentle = TodayTopRules.tiles(me(charge = 24, mode = DayMode.BEREZHNO), Scenario.GENTLE, 300, 460, -1.2)
        assertEquals(TodayTopCopy.BELOW_GENTLE, gentle[0].word)
        assertEquals(TodayTopCopy.BELOW_GENTLE, gentle[1].word)
    }

    @Test
    fun `the scene is one sentence for TalkBack`() {
        val charge = TodayTopRules.tiles(me(charge = 57), Scenario.DAY, 432, 460, 0.0)[0]
        assertEquals("Режим Ровно. Заряд 57, как обычно. День закрыт на 40 %", TodayTopRules.sceneSpoken(DayMode.ROVNO, charge, 0.4f))
        val none = TodayTopRules.tiles(me(charge = null), Scenario.DAY, null, null, null)[0]
        assertEquals("День закрыт на 0 %", TodayTopRules.sceneSpoken(null, none, 0f))
    }

    private val training = DayTask("training", TaskKind.TRAINING, "Силовая · подтягивания", weekGoalId = "body-training", week = TodayCopy.week(1, 3), goal = "Пробежать 5 км")
    private val focus = DayTask("focus", TaskKind.FOCUS, "Главная задача", weekGoalId = "work-focus", week = TodayCopy.week(2, 4))
    private val connect = DayTask("connect_whoop", TaskKind.CONNECT_WHOOP, TodayCopy.TASK_CONNECT_SOURCE)

    @Test
    fun `now is the first open task, the rest keep their order`() {
        val pick = TodayTopRules.now(listOf(training, focus), Scenario.DAY, null)
        assertEquals(training, pick.now)
        assertEquals(listOf(focus), pick.rest)

        val firstDone = TodayTopRules.now(listOf(training.copy(done = true), focus), Scenario.DAY, null)
        assertEquals(focus, firstDone.now)
        assertEquals(listOf("training"), firstDone.rest.map { it.id })

        val allDone = TodayTopRules.now(listOf(training.copy(done = true)), Scenario.DAY, null)
        assertNull(allDone.now)
        assertEquals(1, allDone.rest.size)
        assertEquals(NowPick.EMPTY, TodayTopRules.now(emptyList(), Scenario.DAY, null))
    }

    @Test
    fun `with no source at all the first step is to connect one`() {
        val assemble = DayTask("assemble_week", TaskKind.ASSEMBLE_WEEK, TodayCopy.TASK_ASSEMBLE_WEEK)
        assertEquals(connect, TodayTopRules.now(listOf(assemble, connect), Scenario.NO_SOURCE, null).now)
        // with Health Connect or self-report the week's task goes first as before
        assertEquals(assemble, TodayTopRules.now(listOf(assemble, connect), Scenario.SELF, null).now)
    }

    @Test
    fun `the line of the link goes up to the week goal and the Path`() {
        val week = WeekPlan(
            LocalDate.of(2026, 11, 2),
            listOf(WeekGoal("body-training", Sphere.BODY, TaskKind.TRAINING, "Силовые", target = 3, unit = "раза", progress = 2, source = GoalSource.STAGE)),
        )
        assertEquals("→ Силовые 2 из 3 → Пробежать 5 км", TodayTopRules.link(training, week))
        assertEquals("→ ${TodayCopy.week(1, 3)} → Пробежать 5 км", TodayTopRules.link(training, null))
        assertEquals("→ ${TodayCopy.week(2, 4)}", TodayTopRules.link(focus, week))
        val small = DayTask("start_small", TaskKind.START_SMALL, TodayCopy.TASK_START_SMALL, why = TodayCopy.TASK_START_SMALL_WHY)
        assertEquals(TodayCopy.TASK_START_SMALL_WHY, TodayTopRules.link(small, week))
        assertNull(TodayTopRules.link(connect, week))
        // more progress than the target is shown at the target
        val over = week.copy(goals = week.goals.map { it.copy(progress = 5) })
        assertEquals("→ Силовые 3 из 3 → Пробежать 5 км", TodayTopRules.link(training, over))
    }

    @Test
    fun `more counts the tasks in Russian`() {
        assertEquals("ещё 1 дело", TodayTopCopy.more(1))
        assertEquals("ещё 2 дела", TodayTopCopy.more(2))
        assertEquals("ещё 5 дел", TodayTopCopy.more(5))
        assertEquals("ещё 11 дел", TodayTopCopy.more(11))
        assertEquals("ещё 21 дело", TodayTopCopy.more(21))
    }

    @Test
    fun `T-13 the new words pass the stop dictionary`() {
        TodayTopCopy.all().forEach { assertEquals(emptyList(), StopWords.hits(it), "«$it»") }
    }
}
