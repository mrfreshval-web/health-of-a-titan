package com.healthofatitan.app.domain.safety

import org.junit.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The transitions of the bus (A60 §3, §7 L-05, L-06, L-08, L-13; D-057 п. 2): «норма» → crisis →
 * «норма» after the window; `ED_CARE` enters by its reason, waits out the cooldown, and leaves only
 * by the person's choice; nothing of calories, weight or body photo comes back by itself.
 */
class SafetyBusRulesTest {
    private val day = Duration.ofDays(1).toMillis()
    private val hour = Duration.ofHours(1).toMillis()
    private val t0 = 1_800_000_000_000L
    private var id = 0L

    private fun e(kind: String, payload: String? = null, at: Long = t0) = JournalEvent(++id, kind, payload, at)

    private fun project(vararg events: JournalEvent, now: Long) = SafetyProjector.project(events.toList(), now)

    @Test
    fun `an empty journal is the norm`() {
        val s = project(now = t0)
        assertEquals(SafetyState.NORMAL, s)
        SafetyConsumer.entries.forEach { assertTrue(s.restrictions(it).isEmpty(), "$it") }
    }

    @Test
    fun `crisis level 2 holds 72 hours, then the norm comes back`() {
        val crisis = e(SafetyEvents.CRISIS, "2")
        val during = project(crisis, now = t0 + 71 * hour)
        assertTrue(during.crisis)
        assertTrue(SafetyFlag.CRISIS_L2 in during)
        assertTrue(during.forbids(SafetyConsumer.TODAY, Restriction.HELP_FIRST))
        assertTrue(during.forbids(SafetyConsumer.WEEK, Restriction.NO_PUSH))
        assertTrue(during.forbids(SafetyConsumer.PATH, Restriction.PATH_GENTLE))
        assertFalse(project(crisis, now = t0 + 72 * hour).crisis)
    }

    @Test
    fun `level 1 is not a stop, level 3 beats level 2, a later event extends the window`() {
        assertFalse(project(e(SafetyEvents.CRISIS, "1"), now = t0 + hour).crisis)
        val s = project(e(SafetyEvents.CRISIS, "3"), e(SafetyEvents.CRISIS, "2", t0 + hour), now = t0 + 2 * hour)
        assertTrue(SafetyFlag.CRISIS_L3 in s)
        assertFalse(SafetyFlag.CRISIS_L2 in s)
        val later = project(e(SafetyEvents.CRISIS, "2"), e(SafetyEvents.CRISIS, "2", t0 + 48 * hour), now = t0 + 100 * hour)
        assertTrue(later.crisis, "the second event keeps the window open")
    }

    @Test
    fun `an unreadable crisis level counts as the highest`() {
        assertTrue(SafetyFlag.CRISIS_L3 in project(e(SafetyEvents.CRISIS, "?"), now = t0))
    }

    @Test
    fun `the cooldown follows the reason of entry`() {
        mapOf(EdCareReason.SIGNALS to 7L, EdCareReason.SCREEN to 14L, EdCareReason.SELF_REPORT to 14L, EdCareReason.BODY to 28L, EdCareReason.SPECIALIST to 28L)
            .forEach { (reason, days) ->
                val enter = e(SafetyEvents.ED_CARE_ENTER, reason.code)
                val s = project(enter, now = t0 + day)
                assertTrue(SafetyFlag.ED_CARE in s, reason.name)
                assertEquals(t0 + days * day, s.edCare.cooldownEndsAt, reason.name)
                assertFalse(s.edCare.exitOpen(t0 + days * day - 1))
                assertTrue(s.edCare.exitOpen(t0 + days * day))
            }
    }

    @Test
    fun `ED_CARE never ends by itself, even long after the cooldown`() {
        val s = project(e(SafetyEvents.ED_CARE_ENTER, EdCareReason.SIGNALS.code), now = t0 + 365 * day)
        assertTrue(s.edCare.active)
        assertTrue(SafetyFlag.ED_CARE in s)
        assertEquals(SafetyPolicy.ED_HIDDEN, s.restrictions(SafetyConsumer.TODAY) intersect SafetyPolicy.ED_HIDDEN)
    }

    @Test
    fun `the exit before the cooldown changes nothing, after it only the person's choice ends the mode`() {
        val enter = e(SafetyEvents.ED_CARE_ENTER, EdCareReason.SCREEN.code)
        val early = project(enter, e(SafetyEvents.ED_CARE_EXIT, at = t0 + 3 * day), now = t0 + 20 * day)
        assertTrue(early.edCare.active, "an early «выйти» is not kept for later")
        val chosen = project(enter, e(SafetyEvents.ED_CARE_EXIT, at = t0 + 15 * day), now = t0 + 16 * day)
        assertFalse(chosen.edCare.active)
        assertFalse(SafetyFlag.ED_CARE in chosen)
        assertTrue(chosen.edCare.history)
        assertEquals(EdCareRules.STEP_FOOD_NO_NUMBERS, chosen.edCare.step)
    }

    @Test
    fun `self choice food without numbers opens the exit at once`() {
        val s = project(e(SafetyEvents.ED_CARE_ENTER, EdCareReason.SELF_CHOICE.code), e(SafetyEvents.ED_CARE_EXIT, at = t0 + 1), now = t0 + 2)
        assertFalse(s.edCare.active)
    }

    @Test
    fun `after the exit calories, weight, food windows and body photo do not come back by themselves`() {
        val enter = e(SafetyEvents.ED_CARE_ENTER, EdCareReason.SIGNALS.code)
        val exit = e(SafetyEvents.ED_CARE_EXIT, at = t0 + 8 * day)
        val after = project(enter, exit, now = t0 + 100 * day)
        val week = after.restrictions(SafetyConsumer.WEEK)
        listOf(Restriction.HIDE_CALORIES, Restriction.HIDE_WEIGHT, Restriction.HIDE_FOOD_WINDOWS, Restriction.HIDE_BODY_PHOTO)
            .forEach { assertTrue(it in week, "$it stays hidden") }
        assertFalse(Restriction.HIDE_FOOD_TALK in week, "step 1: food without numbers")

        // step 3 and the weight trend by its own choice: weight comes back, calories never do
        val back = project(
            enter, exit,
            e(SafetyEvents.ED_CARE_STEP, "3", t0 + 9 * day),
            e(SafetyEvents.ED_CARE_RETURN, EdCareReturn.WEIGHT_TREND.code, t0 + 9 * day + 1),
            now = t0 + 10 * day,
        )
        assertFalse(back.forbids(SafetyConsumer.WEEK, Restriction.HIDE_WEIGHT))
        assertTrue(back.forbids(SafetyConsumer.WEEK, Restriction.HIDE_CALORIES))
        assertTrue(back.forbids(SafetyConsumer.PATH, Restriction.HIDE_BODY_PHOTO))
    }

    @Test
    fun `after the exit the fitness age opens only with the weight trend and the form`() {
        val enter = e(SafetyEvents.ED_CARE_ENTER, EdCareReason.SIGNALS.code)
        val exit = e(SafetyEvents.ED_CARE_EXIT, at = t0 + 8 * day)
        val step3 = e(SafetyEvents.ED_CARE_STEP, "3", t0 + 9 * day)
        val weight = e(SafetyEvents.ED_CARE_RETURN, EdCareReturn.WEIGHT_TREND.code, t0 + 9 * day + 1)
        val form = e(SafetyEvents.ED_CARE_RETURN, EdCareReturn.FORM.code, t0 + 9 * day + 2)
        val weightOnly = project(enter, exit, step3, weight, now = t0 + 10 * day)
        assertFalse(weightOnly.forbids(SafetyConsumer.WEEK, Restriction.HIDE_WEIGHT))
        assertTrue(weightOnly.forbids(SafetyConsumer.WEEK, Restriction.NO_FITNESS_AGE), "the waist is not back yet")
        val both = project(enter, exit, step3, weight, form, now = t0 + 10 * day)
        assertFalse(both.forbids(SafetyConsumer.WEEK, Restriction.NO_FITNESS_AGE))
        assertTrue(both.forbids(SafetyConsumer.WEEK, Restriction.HIDE_CALORIES))
    }

    @Test
    fun `review of Agent 3 rows - food windows in pregnancy, fitness age with pulse changers, the path under ED_CARE`() {
        listOf(SafetyFlag.PREGNANT, SafetyFlag.LACTATING).forEach { f ->
            SafetyConsumer.entries.forEach { c -> assertTrue(Restriction.HIDE_FOOD_WINDOWS in SafetyPolicy.of(c, f), "$f $c") }
        }
        listOf(SafetyFlag.MEDS_PSY, SafetyFlag.CHRONIC, SafetyFlag.ATYPICAL_PHYSIO).forEach { f ->
            assertTrue(Restriction.NO_FITNESS_AGE in SafetyPolicy.of(SafetyConsumer.WEEK, f), "$f")
        }
        assertEquals(SafetyPolicy.ED_HIDDEN, SafetyPolicy.of(SafetyConsumer.PATH, SafetyFlag.ED_CARE))
    }

    @Test
    fun `nothing is taken back before step 3, and a new entry closes everything again`() {
        val enter = e(SafetyEvents.ED_CARE_ENTER, EdCareReason.SIGNALS.code)
        val exit = e(SafetyEvents.ED_CARE_EXIT, at = t0 + 8 * day)
        val early = project(enter, exit, e(SafetyEvents.ED_CARE_RETURN, EdCareReturn.WEIGHT_TREND.code, t0 + 9 * day), now = t0 + 10 * day)
        assertTrue(early.forbids(SafetyConsumer.WEEK, Restriction.HIDE_WEIGHT))
        val again = project(
            enter, exit,
            e(SafetyEvents.ED_CARE_STEP, "3", t0 + 9 * day),
            e(SafetyEvents.ED_CARE_RETURN, EdCareReturn.WEIGHT_TREND.code, t0 + 9 * day + 1),
            e(SafetyEvents.ED_CARE_ENTER, EdCareReason.BODY.code, t0 + 10 * day),
            now = t0 + 11 * day,
        )
        assertTrue(again.edCare.active)
        assertTrue(again.edCare.returned.isEmpty())
        assertEquals(t0 + 38 * day, again.edCare.cooldownEndsAt)
    }

    @Test
    fun `a second entry while active keeps the longer cooldown`() {
        val s = project(
            e(SafetyEvents.ED_CARE_ENTER, EdCareReason.BODY.code),
            e(SafetyEvents.ED_CARE_ENTER, EdCareReason.SIGNALS.code, t0 + day),
            now = t0 + 2 * day,
        )
        assertEquals(EdCareReason.BODY, s.edCare.reason)
        assertEquals(t0 + 28 * day, s.edCare.cooldownEndsAt)
    }

    @Test
    fun `an unknown safety event means forbid for every consumer`() {
        val s = project(e("safety.something_new", "x"), now = t0)
        assertTrue(s.unknown)
        SafetyConsumer.entries.forEach { assertEquals(SafetyPolicy.strictest(it), s.restrictions(it)) }
        assertTrue(project(e(SafetyEvents.ED_CARE_ENTER, "???"), now = t0).unknown)
        // events of other modules in the same journal are not the bus's business
        assertEquals(SafetyState.NORMAL, project(e("path.goal_taken"), now = t0))
    }

    @Test
    fun `every consumer has a policy for every flag and the crisis is the strictest for help`() {
        SafetyConsumer.entries.forEach { c ->
            SafetyFlag.entries.forEach { f -> SafetyPolicy.of(c, f) }
        }
        assertTrue(Restriction.HELP_FIRST in SafetyPolicy.of(SafetyConsumer.TODAY, SafetyFlag.CRISIS_L2))
        assertTrue(Restriction.HELP_FIRST in SafetyPolicy.of(SafetyConsumer.WEEK, SafetyFlag.CRISIS_L3))
        SafetyConsumer.entries.forEach { c -> assertTrue(SafetyPolicy.ED_HIDDEN.any { it in SafetyPolicy.of(c, SafetyFlag.ED_CARE) }, "$c") }
    }

    @Test
    fun `the crisis and ED_CARE together keep both`() {
        val s = project(e(SafetyEvents.ED_CARE_ENTER, EdCareReason.SCREEN.code), e(SafetyEvents.CRISIS, "2", t0 + hour), now = t0 + 2 * hour)
        assertTrue(s.crisis)
        assertTrue(SafetyFlag.ED_CARE in s)
        val today = s.restrictions(SafetyConsumer.TODAY)
        assertTrue(Restriction.HELP_FIRST in today && Restriction.HIDE_CALORIES in today)
    }

    @Test
    fun `events from the future are not applied yet`() {
        assertNull(project(e(SafetyEvents.CRISIS, "3", t0 + day), now = t0).flags[SafetyFlag.CRISIS_L3])
    }

    @Test
    fun `the training reads the bus - a crisis hides it, sickness leaves mobility, clinical flags cap the intensity`() {
        val crisis = project(e(SafetyEvents.CRISIS, "2"), now = t0 + hour)
        assertTrue(crisis.forbids(SafetyConsumer.TRAINING, Restriction.HELP_FIRST))
        assertTrue(crisis.forbids(SafetyConsumer.TRAINING, Restriction.NO_LOAD))
        assertEquals(setOf(Restriction.NO_LOAD), SafetyPolicy.of(SafetyConsumer.TRAINING, SafetyFlag.SICK))
        listOf(
            SafetyFlag.PREGNANT, SafetyFlag.POSTPARTUM_12M, SafetyFlag.LACTATING, SafetyFlag.MEDS_HR, SafetyFlag.MEDS_PSY,
            SafetyFlag.CHRONIC, SafetyFlag.INJURY, SafetyFlag.SCREEN_NOT_CLEARED, SafetyFlag.AGE_65P,
        ).forEach { assertTrue(Restriction.INTENSITY_CAP in SafetyPolicy.of(SafetyConsumer.TRAINING, it), "$it") }
        val ed = project(e(SafetyEvents.ED_CARE_ENTER, EdCareReason.SIGNALS.code), now = t0 + hour)
        assertTrue(ed.forbids(SafetyConsumer.TRAINING, Restriction.HIDE_FOOD_TALK))
    }

    @Test
    fun `an unknown state forbids the training everything it can forbid`() {
        val strictest = SafetyPolicy.strictest(SafetyConsumer.TRAINING)
        listOf(Restriction.HELP_FIRST, Restriction.NO_LOAD, Restriction.INTENSITY_CAP).forEach { assertTrue(it in strictest, "$it") }
    }
}
