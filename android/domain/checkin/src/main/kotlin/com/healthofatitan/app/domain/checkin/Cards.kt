package com.healthofatitan.app.domain.checkin

/** When a card is asked (A44 §7 `phase`). WEEK is the Sunday question of the evening. */
enum class Phase { WAKE, RITUAL, EVENING, WEEK }

/**
 * The cards of A44 v0.2 (§1.2–§1.4) with their `cardId@ver` (§7). Evening «Тело» and «Алкоголь»
 * are not here: they stay the rows of «Закрыть день» (stream A, `evening_check`) — one evening.
 *
 * @param private S5 «Только ты» (§6.4): stored encrypted in its own table, never leaves the phone,
 *   never goes to the mentor, pushes, widgets, the server or an LLM.
 * @param core asked even in «Быстро» (П1 + П2); never offered for removal (§3 «Быстро» always 2)
 */
enum class CardId(val key: String, val version: Int, val phase: Phase, val private: Boolean = false, val core: Boolean = false) {
    CHARGE("charge", 1, Phase.WAKE, core = true),
    WEATHER("weather", 1, Phase.WAKE, core = true),
    BODY("body", 1, Phase.WAKE),
    HEAD("head", 1, Phase.WAKE),
    DRIVE("drive", 1, Phase.WAKE),
    SLEPT("slept", 1, Phase.WAKE),
    YESTERDAY("yesterday", 1, Phase.WAKE),

    /** П7 «Только ты»: one card in the deck, stored as `intimacy@1` and `pulls@1`. */
    ONLY_YOU("only_you", 1, Phase.WAKE, private = true),
    INTIMACY("intimacy", 1, Phase.WAKE, private = true),
    PULLS("pulls", 1, Phase.WAKE, private = true),

    RITUAL("ritual", 1, Phase.RITUAL),
    CHARGE_AFTER("charge_after", 1, Phase.RITUAL),

    EVENING_WEATHER("evening_weather", 1, Phase.EVENING),
    /** Evening body zones («забито → где», sc5): the body answer itself stays in `evening_check`. */
    EVENING_AREAS("evening_areas", 1, Phase.EVENING),

    WEEK_PEOPLE("week_people", 1, Phase.WEEK),
    WEEK_CLOSENESS("week_closeness", 1, Phase.WEEK, private = true),
    ;

    val ref: String get() = "$key@$version"

    companion object {
        fun fromRef(ref: String): CardId? = entries.firstOrNull { it.ref == ref }
    }
}

/** П1 / Р2 «Заряд»: light fills the Titan from below, five levels (A44 §1.2). */
object Charge {
    const val MIN = 1
    const val MAX = 5

    /** `SelfReport.energy` ← `charge@1` (A44 §7: 2 / 4 / 6 / 8 / 10). */
    fun energy(level: Int): Int = level.coerceIn(MIN, MAX) * 2
}

/** П2 / В-3 «Погода внутри»: the sky over the Titan (A44 §1.2). Level 1…5. */
enum class Sky(val level: Int) {
    STORM(1), RAIN(2), CLOUDY(3), BREAK(4), CLEAR(5);

    companion object {
        fun of(level: Int?): Sky? = entries.firstOrNull { it.level == level }
    }
}

/** «+ словами» under the weather (How We Feel: the word comes after the axis, A44 §2). */
enum class Mood { CALM, GLAD, TIRED, ANXIOUS, IRRITATED, EMPTY }

/** П3 «Тело»: легко · ноет/забито → где · простуда. */
enum class BodyFeel(val level: Int) {
    LIGHT(3), SORE(2), SICK(1);

    companion object {
        fun of(level: Int?): BodyFeel? = entries.firstOrNull { it.level == level }
    }
}

/** Zones of the silhouette «Где?» (sc5). Names only, never a diagnosis or an injury. */
enum class BodyArea { NECK, SHOULDERS, CHEST, ARMS, BACK, LOWER_BACK, HIPS, KNEES, CALVES }

/** «Это боль» (A44 §6.2, sc5): separated from «забито». */
enum class Pain {
    /** «Давит или жжёт в груди» → SAF-01: the emergency line, training paused. */
    CHEST,

    /** A joint → TR-09 / F6: no load on that zone today. */
    JOINT,
}

/** П4 «Голова»: туман · средне · ясно (the Titan's sharpness). */
enum class Head(val level: Int) {
    FOG(1), MID(2), CLEAR(3);

    companion object {
        fun of(level: Int?): Head? = entries.firstOrNull { it.level == level }
    }
}

/** П5 «Тяга к делу»: нет сил · норм · рвусь (the Titan sinks / rises). */
enum class Drive(val level: Int) {
    NONE(1), OK(2), EAGER(3);

    companion object {
        fun of(level: Int?): Drive? = entries.firstOrNull { it.level == level }
    }
}

/** П6 «Вчера вечером»: behaviour only, as icons. Alcohol asks «сколько». */
enum class Yesterday { SCREENS_LATE, LATE_FOOD, ALCOHOL, SWEETS, COFFEE_LATE, NOTHING }

/** П7 «Близость» (§6.4). Words on the buttons: не было · с партнёром · сам с собой · не отмечать. */
enum class Intimacy { NONE, PARTNER, SELF, NO_MARK }

/** П7 «Что тянуло?» (§6.4): a pull, never «зависимость» or «слабость». */
enum class Pull { FEED, VIDEO, GAMES, SWEETS, SHOPPING, ADULT, NOTHING }

/**
 * Р1 «Утро как вчера?»: the morning stack as icons. Supplements are the user's own stack items
 * (`stack:<name>` as the person typed it): the fact of taking only, never a dose (veto №3 of Agent 3).
 */
enum class RitualItem { COFFEE, COLD_SHOWER, LIGHT, BREATH, WATER }

object Stack {
    const val PREFIX = "stack:"

    fun own(name: String): String = PREFIX + name.trim()

    fun isOwn(choice: String): Boolean = choice.startsWith(PREFIX)

    fun nameOf(choice: String): String = choice.removePrefix(PREFIX)
}

/**
 * One answer (A25 §7 `CardResponse`). A skip is stored as [skipped] = true with no value: a skip is
 * never a zero (A25 §2.1, A44 §1).
 *
 * @param level the one-tap level of the card (charge 1–5, sky 1–5, body, head, drive, week 1–5)
 * @param choices names of the multiselect (Yesterday, Pull, RitualItem, Mood, BodyArea, `stack:…`)
 * @param count alcohol servings for «Вчера» (1…4, 4 = 4+)
 * @param atMs when it was answered: the ritual delta needs the time
 */
data class Answer(
    val card: CardId,
    val level: Int? = null,
    val choices: Set<String> = emptySet(),
    val count: Int? = null,
    val skipped: Boolean = false,
    val atMs: Long = 0L,
) {
    fun has(choice: Enum<*>): Boolean = choice.name in choices

    companion object {
        fun skip(card: CardId, atMs: Long = 0L) = Answer(card, skipped = true, atMs = atMs)
    }
}

/**
 * «Только ты» of one morning (§6.4): closeness, pulls and the optional «хотелось меньше». Lives
 * only in the S5 table; [encode] gives the plain text that the repository encrypts at once.
 */
data class PrivateAnswer(
    val intimacy: Intimacy? = null,
    val pulls: Set<Pull> = emptySet(),
    val wantLess: Boolean = false,
) {
    val empty: Boolean get() = intimacy == null && pulls.isEmpty() && !wantLess
}
