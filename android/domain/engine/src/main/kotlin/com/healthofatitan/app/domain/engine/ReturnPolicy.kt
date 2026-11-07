package com.healthofatitan.app.domain.engine

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** How the app meets a person after a pause (A10 С6, A18 И4.4, RET-01). */
enum class ReturnStage {
    /** Opened yesterday or today: an ordinary day. */
    NONE,

    /** Silence ≥ 2 days: the minimal version of the plan, warm words, no report for the pause. */
    RETURN,

    /** Silence ≥ 7 days: «С возвращением» once on the first open, then the minimal plan. */
    WELCOME_BACK,
}

/**
 * Return after a pause without shame (A18 И4.4, A09 RET-01, A03b §3.3). Pure date arithmetic:
 * «silence» is whole days in which HoT was not opened at all. No counters of missed days are
 * ever shown; the words «пропустил», «серия» do not exist here.
 */
object ReturnPolicy {

    /** RET-01: silence ≥ 2 days. */
    const val RETURN_DAYS = 2

    /** A18 И4.4: after 7+ days the «С возвращением» screen. */
    const val WELCOME_DAYS = 7

    /** The nudge is quiet: only in the day, never at night (A11 §2: ночью тишина). */
    val NUDGE_FROM: LocalTime = LocalTime.of(10, 0)
    val NUDGE_UNTIL: LocalTime = LocalTime.of(20, 0)

    /**
     * Whole days without opening HoT between the last open and [today]: last open yesterday → 0,
     * on Monday with today Thursday → 2 (Tuesday and Wednesday). Never opened → 0: a first day is
     * not a return.
     */
    fun silenceDays(lastOpen: LocalDate?, today: LocalDate): Int {
        if (lastOpen == null) return 0
        return (ChronoUnit.DAYS.between(lastOpen, today) - 1).toInt().coerceAtLeast(0)
    }

    fun stage(silenceDays: Int): ReturnStage = when {
        silenceDays >= WELCOME_DAYS -> ReturnStage.WELCOME_BACK
        silenceDays >= RETURN_DAYS -> ReturnStage.RETURN
        else -> ReturnStage.NONE
    }

    /**
     * The warm local nudge (A18 И4.4 ①②): once per pause, when the silence has reached
     * [RETURN_DAYS] whole days, in the day window. [lastNudge] after [lastOpen] means this pause
     * was already met: no second message, no nagging.
     */
    fun nudgeDue(lastOpen: LocalDate?, lastNudge: LocalDate?, now: LocalDateTime): Boolean {
        if (lastOpen == null) return false
        val silence = silenceDays(lastOpen, now.toLocalDate())
        if (silence < RETURN_DAYS) return false
        if (lastNudge != null && lastNudge > lastOpen) return false
        val t = now.toLocalTime()
        return !t.isBefore(NUDGE_FROM) && t.isBefore(NUDGE_UNTIL)
    }

    /**
     * «Новый старт» (A18 И4.4 ③, Dai, Milkman & Riis): the nearest Monday or 1st of the month,
     * today included.
     */
    fun newStart(today: LocalDate): LocalDate {
        if (today.dayOfWeek == DayOfWeek.MONDAY || today.dayOfMonth == 1) return today
        val monday = today.plusDays((DayOfWeek.MONDAY.value + 7L - today.dayOfWeek.value) % 7)
        val first = today.withDayOfMonth(1).plusMonths(1)
        return minOf(monday, first)
    }
}

/**
 * RET-01 (A09 §3, content/rules/return.yaml) on top of the day plan: after silence ≥ 2 days the
 * plan keeps the regime but becomes the minimal version — 10 minutes of movement instead of the
 * training, one main task — and the push greets instead of reporting (A11 §2 «Возвращение»).
 * Safety first: a sick day keeps its rest (SAF-03), the crisis protocol precedes (SAF-02).
 */
object ReturnMode {

    const val MINIMAL_MINUTES = 10
    const val FOCUS_MINUTES = 45

    fun apply(plan: DayPlan, silenceDays: Int): DayPlan {
        if (ReturnPolicy.stage(silenceDays) == ReturnStage.NONE) return plan
        val sick = DayFlag.SICK in plan.flags
        val training = if (sick || plan.training.type == TrainingType.REST) {
            plan.training
        } else {
            TrainingChoice(
                type = TrainingType.WALK,
                level = TrainingLevel.L2,
                templateId = null,
                minutes = MINIMAL_MINUTES,
                startMin = plan.training.startMin,
                replaced = plan.training.type,
            )
        }
        val items = plan.items.map { item ->
            when {
                item.id == DayEngine.Ids.TRAINING && training !== plan.training -> item.copy(
                    rule = RuleSet.RET_01,
                    endMin = item.startMin?.let { it + MINIMAL_MINUTES },
                    title = DayTexts.trainingTitle(training),
                    detail = DayTexts.RETURN_TRAINING_DETAIL,
                )
                item.id == DayEngine.Ids.FOCUS -> item.copy(
                    rule = RuleSet.WRK_02,
                    endMin = item.startMin?.let { it + FOCUS_MINUTES },
                    detail = DayTexts.focusDetail(1, FOCUS_MINUTES),
                )
                else -> item
            }
        }
        return plan.copy(
            flags = plan.flags + DayFlag.RETURN,
            training = training,
            items = items,
            fired = plan.fired + FiredRule(RuleSet.RET_01, DayTexts.RETURN_WHY),
            headline = DayTexts.RETURN_HEADLINE,
            summary = DayTexts.RETURN_SUMMARY,
        )
    }
}
