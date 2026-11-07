package com.healthofatitan.app.feature.today

import com.healthofatitan.app.core.ui.component.HotMode
import com.healthofatitan.app.core.ui.component.StatusRings
import com.healthofatitan.app.domain.engine.Regime
import com.healthofatitan.app.integration.whoop.day.HeaderRings
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * New microcopy of «Титаниум» on «Сегодня», «Рассвет» and «Печать» (A37). Labels and telemetry
 * only: the texts of the day (regime, plan, evening) stay in DayTexts / DayUiTexts and are not
 * changed here. Review: Agent 12 (UX copy), Agent 10 (tone), Agent 3 (anything about the body).
 */
object TitanTexts {
    private val ru: Locale = Locale.forLanguageTag("ru")
    private val dayMonth = DateTimeFormatter.ofPattern("EEEE · d MMMM", ru)
    private val hhmm = DateTimeFormatter.ofPattern("HH:mm", ru)

    const val NOW = "Сейчас"
    const val NEXT = "Дальше"
    const val DAY_LINE = "Линия дня"
    const val MARK_DONE = "Отметить сделанным"
    const val MARKED = "Отмечено"
    const val CHARGE_PILL = "Заряд"
    const val NORM_PILL = "норма"
    const val START_DAY = "Начать день"
    const val SLEEP = "сон"
    const val CHARGE = "заряд"
    const val CEILING = "потолок"
    const val PULSE = "пульс"
    const val SEAL_DONE = "Готово"
    const val SEAL_SPOKEN = "Тренировку подтвердил WHOOP, кольцо замкнулось"
    const val MINUTES = "мин"
    const val BEDTIME = "отбой"

    /** A39 §7.1: the link on the safety card (SAF-03@2); wording pending Agent 7. */
    const val SAFETY_MORE = "Что делать"

    /** «среда · 11 ноября». */
    fun date(day: LocalDate): String = dayMonth.format(day)

    /** «WHOOP · 06:41». */
    fun synced(at: Long?, zone: ZoneId = ZoneId.systemDefault()): String =
        "WHOOP" + (at?.let { " · " + hhmm.format(Instant.ofEpochMilli(it).atZone(zone)) } ?: "")

    /** Y7: one line for the user, «WHOOP · синхронизировано в 06:41»; the log lives in a hidden place. */
    fun syncedLine(at: Long?, zone: ZoneId = ZoneId.systemDefault()): String =
        at?.let { "WHOOP · синхронизировано в " + hhmm.format(Instant.ofEpochMilli(it).atZone(zone)) } ?: "WHOOP"

    /** «5:50». */
    fun hm(hours: Double): String {
        val m = (hours * 60).roundToInt()
        return "${m / 60}:${(m % 60).toString().padStart(2, '0')}"
    }

    /** «12» or «7,5». */
    fun ceiling(v: Double): String = if (v == v.roundToInt().toDouble()) v.roundToInt().toString() else String.format(ru, "%.1f", v)

    /** TalkBack for the dawn numbers. */
    fun dawnSpoken(sleep: Double?, charge: Int?, ceiling: Double): String = listOfNotNull(
        sleep?.let { "Сон ${hm(it)}" },
        charge?.let { "Заряд $it из 100, 50 — твоя норма" },
        "Потолок нагрузки ${ceiling(ceiling)}",
    ).joinToString(", ")

    /** TalkBack for the hero: «Режим Ровно, Заряд 44». */
    fun heroSpoken(mode: HotMode?, charge: Int?): String = listOfNotNull(
        mode?.let { "Режим ${it.word}" },
        charge?.let { "Заряд $it из 100, 50 — твоя норма" },
    ).joinToString(", ").ifEmpty { "Титан ждёт данные WHOOP" }
}

/** The D-029 header rings as the top bar draws them. */
fun HeaderRings.toStatusRings(): StatusRings = StatusRings(
    strain = strain,
    strainCeiling = strainCeiling,
    steps = steps,
    stepsGoal = stepsGoal,
    untilBedMin = untilBedMin,
    dayFraction = dayFraction,
    bedtimeMin = bedtimeMin,
    mode = regime?.mode(),
)

fun Regime.mode(): HotMode = when (this) {
    Regime.BOOST -> HotMode.RAZGON
    Regime.STEADY -> HotMode.ROVNO
    Regime.GENTLE -> HotMode.BEREZHNO
}
