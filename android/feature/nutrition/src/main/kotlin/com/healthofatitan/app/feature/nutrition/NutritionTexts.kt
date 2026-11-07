package com.healthofatitan.app.feature.nutrition

import com.healthofatitan.app.core.model.profile.AllergySeverity
import com.healthofatitan.app.domain.nutrition.EveningMark
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Interface words of the food screens (labels, buttons). Everything that explains or advises —
 * `why_ru`, hints, allergen and diet names, the disclaimer — comes from `content/` only.
 *
 * TODO(review: Agent 10 — words about food and body; Agent 7 — consent and storage lines): the
 * lines below are drafts of Agent 13 after A41 §4.2, §6.5 and §10; content/nutrition and
 * content/profile are `status: draft` and wait for Agents 3 and 10 as well.
 */
object NutritionTexts {
    const val CARD_OVERLINE = "Еда"
    const val PLATE_OF_DAY = "Тарелка дня"
    const val UNIVERSAL_PLATE = "Тарелка из того, что тебе подходит"
    const val CHIP_NO_TIME = "Нет времени"
    const val CHIP_TRAVEL = "В дороге"
    const val AFTER_TRAINING = "после тренировки"
    const val OPTIONAL = "если голоден"
    const val CHECKED = "Проверено по твоему профилю"
    const val REPLACE = "Заменить"
    const val WHY = "Почему"
    const val COMPOSITION = "Состав"
    const val SET_UP = "Особенности"
    const val LEVEL = "Уровень"

    // A41 §4.2 (Agent 10 to confirm): lowering is never a punishment.
    const val LOWERED = "Сегодня план попроще — так легче восстановиться. Твой уровень на месте."

    // A41 §6.5 (Agent 7 to confirm): without consent nothing is filtered.
    const val NO_CONSENT = "Аллергены и тип питания не учитываются — отметь их в «Особенностях»."

    const val EVENING_QUESTION = "Еда сегодня"
    const val EVENING_SAVED = "Принято"

    fun eveningWord(mark: EveningMark): String = when (mark) {
        EveningMark.PLATE -> "по тарелке"
        EveningMark.PARTLY -> "частично"
        EveningMark.ATE -> "просто поел"
    }

    // Profile screen (mockup n2)
    const val PROFILE_TITLE = "Еда и тело"
    const val PROFILE_BACK = "Настройки · Еда и тело"
    const val ONLY_ON_PHONE = "только на телефоне"
    const val DELETE_ALL = "удалить всё"
    const val DELETE = "удалить"
    const val ALLERGENS = "Аллергены · ЕС 14 + США 9"
    /** Short badges of `allergy_severity` («Избегаю», «Аллергия», «Тяжёлая реакция (анафилаксия)»). */
    val SEVERITY_SHORT = mapOf(
        AllergySeverity.AVOID to "избегаю",
        AllergySeverity.ALLERGY to "аллергия",
        AllergySeverity.ANAPHYLAXIS to "тяжёлая",
    )
    const val NOT_MARKED = "не отмечено"
    const val ALLERGEN_TAP = "Тап — избегаю, ещё тап — аллергия, ещё — тяжёлая реакция"
    const val DIETS = "Тип питания"
    const val INTOLERANCES = "Непереносимости"
    const val CONDITIONS = "Состояния по словам врача"
    const val CONDITIONS_NOTE = "Не диагноз: план станет проще и безопаснее, остальное — с врачом."
    const val INJURIES = "Травмы и ограничения · для тренировок"

    // Consent (A41 §6.5, Agent 7): separate, explicit, one screen, before the first field.
    const val CONSENT_TITLE = "Данные о здоровье"
    const val CONSENT_TEXT =
        "Аллергии, тип питания и состояния — данные о здоровье. Они хранятся только на этом телефоне, " +
            "зашифрованными, не попадают в уведомления и аналитику. Удалить можно по разделам или всё сразу; " +
            "отзыв согласия удаляет всё."
    const val CONSENT_YES = "Согласен"
    const val CONSENT_REVOKE = "отозвать согласие"

    fun consentDate(ms: Long): String =
        "согласие " + DateTimeFormatter.ofPattern("dd.MM", Locale.ROOT).format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

    // Level screen (mockup n1)
    const val LEVEL_TITLE = "Уровень питания"
    const val LEVEL_BACK = "Еда · Уровень"
    const val LEVEL_LOCKED = "закрыт безопасным режимом"
    const val LEVEL_LATER = "после ревью Агента 3"
    const val LEVEL_CHOSEN = "выбран"

    // Meal card (mockup n4)
    const val MEAL_BACK = "Сегодня · Еда"
    const val MAY_CONTAIN = "может содержать: "
    const val RESTAURANT = "В кафе уточни состав у официанта."

    fun minutes(n: Int): String = "$n мин"

    fun clock(min: Int): String {
        val m = ((min % 1440) + 1440) % 1440
        return String.format(Locale.ROOT, "%02d:%02d", m / 60, m % 60)
    }

    /** Hand portions without decimals: 0.5 → ½, 1.5 → 1½. */
    fun hands(n: Double): String {
        val whole = n.toInt()
        val half = n - whole >= 0.5
        return when {
            whole == 0 && half -> "½"
            half -> "$whole½"
            else -> "$whole"
        }
    }

    fun handWord(role: String): String = when (role) {
        "protein" -> "ладонь"
        "starch", "fruit" -> "горсть"
        "veg" -> "кулак"
        else -> "палец"
    }

    fun roleWord(role: String): String = when (role) {
        "protein" -> "белок"
        "starch" -> "крупа"
        "veg" -> "овощи"
        "fruit" -> "фрукты"
        "fat" -> "жир"
        else -> role
    }

    const val CAFFEINE = "кофе до"
}
