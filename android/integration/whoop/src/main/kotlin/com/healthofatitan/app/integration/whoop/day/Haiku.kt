package com.healthofatitan.app.integration.whoop.day

import com.healthofatitan.app.domain.engine.DayFlag
import com.healthofatitan.app.domain.engine.Regime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.random.Random

/** One haiku of content/haiku/library.yaml (Agent 16, STYLE.md §5). */
data class Haiku(
    val id: String,
    val mode: Regime,
    val tags: Set<String>,
    val image: String,
    val text: String,
    val retired: Boolean = false,
) {
    val lines: List<String> get() = text.split('\n')
}

/** A morning already shown (the dawn log): what may not repeat. */
data class ShownHaiku(val day: LocalDate, val id: String?, val textKey: String?, val image: String?)

/** What the morning knows (from the stored day and the derived metrics, never raw WHOOP data). */
data class DawnFacts(
    val day: LocalDate,
    val regime: Regime,
    val flags: Set<DayFlag> = emptySet(),
    val sleepHours: Double? = null,
    /** ORS@1 of the night, z units. */
    val orsZ: Double? = null,
    /** Весы LRB@1 of the day (load against recovery). */
    val lrb: Double? = null,
    /** Crisis signals (A07 CrisisGuard: CARE / EMERGENCY): no haiku at all. */
    val crisis: Boolean = false,
    val southern: Boolean = false,
)

/**
 * The haiku library as compiled by `contentCompile` (content/haiku/library.yaml →
 * assets/content/haiku/library.json). Unknown modes and retired haiku are dropped.
 */
object HaikuLibrary {
    const val ASSET = "content/haiku/library.json"

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): List<Haiku> {
        val root = json.parseToJsonElement(text).jsonObject
        val items = root["haiku"]?.jsonArray ?: return emptyList()
        return items.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val mode = modeOf(o["mode"]?.jsonPrimitive?.content) ?: return@mapNotNull null
            Haiku(
                id = o["id"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                mode = mode,
                tags = o["tags"]?.jsonArray?.map { it.jsonPrimitive.content }?.toSet().orEmpty(),
                image = o["image"]?.jsonPrimitive?.content.orEmpty(),
                text = o["text"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                retired = o["retired"]?.jsonPrimitive?.booleanOrNull == true,
            )
        }.filter { !it.retired && it.lines.size == 3 }
    }

    /** STYLE.md §5: razgon = BOOST, ravno = STEADY, berezhno = GENTLE. */
    fun modeOf(key: String?): Regime? = when (key) {
        "razgon" -> Regime.BOOST
        "ravno" -> Regime.STEADY
        "berezhno" -> Regime.GENTLE
        else -> null
    }
}

/**
 * Choice and rotation of the morning haiku, content/haiku/STYLE.md §6 (for Agent 13):
 * mode → truth of the tags → never repeat an id or a text → a key image not more than once in
 * 7 days → the most specific first, ties in a seeded order (date + user) → nothing fits: no haiku
 * («повтор хуже, чем тишина»). §6.3: crisis — no haiku; SICK — only «Бережно» with `any` and no
 * images of effort; no data (DATA_LIMITED, CALIBRATION) — only «Ровно» with `any`.
 */
object HaikuPicker {
    const val IMAGE_PAUSE_DAYS = 7L

    /** Images of effort that a sick morning does not get (STYLE.md §6.3 «тетива, кузня, подъём»). */
    val EFFORT_IMAGES = setOf("bow", "anvil", "mountain")

    /** STYLE.md §6.1, thresholds of A09 (sleep 6 / 7 h, ORS −1 / +0.5 z, Весы > 1). */
    fun dayTags(f: DawnFacts): Set<String> = buildSet {
        f.sleepHours?.let {
            if (it < 6.0) add("sleep_short")
            if (it >= 7.0) add("sleep_good")
        }
        f.orsZ?.let {
            if (it <= -1.0) add("recovery_low")
            if (it >= 0.5) add("recovery_high")
        }
        if ((f.lrb ?: 0.0) > 1.0) add("strain_high")
        add(if (f.day.dayOfWeek == DayOfWeek.SATURDAY || f.day.dayOfWeek == DayOfWeek.SUNDAY) "weekend" else "workday")
        add(season(f.day, f.southern))
        add("any")
    }

    fun season(day: LocalDate, southern: Boolean = false): String {
        val north = when (day.monthValue) {
            12, 1, 2 -> "season_winter"
            3, 4, 5 -> "season_spring"
            6, 7, 8 -> "season_summer"
            else -> "season_autumn"
        }
        if (!southern) return north
        return when (north) {
            "season_winter" -> "season_summer"
            "season_summer" -> "season_winter"
            "season_spring" -> "season_autumn"
            else -> "season_spring"
        }
    }

    /** Lower case, ё → е, letters only: catches the same text under another id. */
    fun textKey(text: String): String =
        text.lowercase().replace('ё', 'е').filter { it.isLetter() || it == ' ' || it == '\n' }
            .replace(Regex("\\s+"), " ").trim()

    fun pick(library: List<Haiku>, facts: DawnFacts, history: List<ShownHaiku>, userId: String): Haiku? {
        if (facts.crisis) return null
        val sick = DayFlag.SICK in facts.flags
        val noData = DayFlag.DATA_LIMITED in facts.flags || DayFlag.CALIBRATION in facts.flags
        val mode = when {
            sick -> Regime.GENTLE
            noData -> Regime.STEADY
            else -> facts.regime
        }
        val today = dayTags(facts)
        val shownIds = history.mapNotNull { it.id }.toSet()
        val shownTexts = history.mapNotNull { it.textKey }.toSet()
        val recentImages = history
            .filter { it.image != null && it.day.isAfter(facts.day.minusDays(IMAGE_PAUSE_DAYS)) && !it.day.isAfter(facts.day) }
            .mapNotNull { it.image }
            .toSet()
        val fits = library.filter { h ->
            h.mode == mode &&
                today.containsAll(h.tags) &&
                h.id !in shownIds &&
                textKey(h.text) !in shownTexts &&
                h.image !in recentImages &&
                (!(sick || noData) || h.tags == setOf("any")) &&
                (!sick || h.image !in EFFORT_IMAGES)
        }
        if (fits.isEmpty()) return null
        val seeded = fits.shuffled(Random(("${facts.day}|$userId").hashCode()))
        return seeded.maxByOrNull { h -> h.tags.count { it != "any" } }
    }
}
