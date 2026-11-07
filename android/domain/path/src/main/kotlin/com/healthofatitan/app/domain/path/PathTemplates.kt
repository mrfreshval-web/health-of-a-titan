package com.healthofatitan.app.domain.path

/**
 * A template of a goal (A55 §2, 10 for the MVP; Агенты 8, 12, 3). Every template has Water in the
 * role «бережёт» or higher (P-03). Criteria are ranges (P-06).
 *
 * @param review the template waits for the review of Agents 10, 3, 7 (A55 §5: 1, 8, 10)
 */
data class GoalTemplate(
    val id: String,
    val title: String,
    val kind: GoalKind,
    val keywords: List<String>,
    val components: List<Pair<ComponentKey, Role>>,
    val criteria: List<Criterion>,
    val obstacleHint: String,
    val review: Boolean = false,
    /** «Крепко после 50–60» asks PAR-Q+ before any test of Земля (A19 §6.1). */
    val parq: Boolean = false,
) {
    /** Behind the flag while the safe pace tables are missing (D-047): weight or strength leads. */
    val weightOrStrength: Boolean
        get() = components.any { (k, r) -> k.weightOrStrength && r == Role.LEAD } ||
            criteria.any { it.metric?.component?.weightOrStrength == true }

    /** Closed in `ed_care` (A56 §2.2): «Форма» and «Топливо» goals. */
    val touchesWeight: Boolean get() = components.any { (k, _) -> k == ComponentKey.BODY }
}

object PathTemplates {
    const val FORM = "form"
    const val PULLUP = "pullup"
    const val RACE = "race"
    const val PROMOTION = "promotion"
    const val LANGUAGE = "language"
    const val PROJECT = "project"
    const val ENERGY = "energy"
    const val CALM = "calm"
    const val CLOSER = "closer"
    const val STRONG = "strong"

    /** The template of a goal assembled by hand (A56 §2.1 «Своя цель»). */
    const val CUSTOM = "custom"

    /** A goal of the 12-week path before v11 (A42): kept as it was, read by the new screen. */
    const val LEGACY = "legacy"

    private fun c(label: String, low: Double, high: Double, unit: String, metric: PathMetric) = Criterion(label, low, high, unit, metric)

    val ALL: List<GoalTemplate> = listOf(
        GoalTemplate(
            FORM, "Форма к дате", GoalKind.HAVE,
            listOf("фигур", "форм", "похуд", "рельеф", "талия", "пресс", "вес", "кг"),
            listOf(
                ComponentKey.STRENGTH to Role.LEAD, ComponentKey.BODY to Role.LEAD, ComponentKey.FUEL to Role.PROP,
                ComponentKey.CLARITY to Role.PROP, ComponentKey.SLEEP to Role.GUARD, ComponentKey.FEELINGS to Role.GUARD,
            ),
            listOf(c("талия", 4.0, 6.0, "см", PathMetric.WAIST), c("силовые в неделю", 2.0, 3.0, "", PathMetric.STRENGTH_SESSIONS)),
            "вечером ем «на нервах»",
            review = true,
        ),
        GoalTemplate(
            PULLUP, "Первое подтягивание", GoalKind.DO,
            listOf("подтяг", "турник"),
            listOf(ComponentKey.STRENGTH to Role.LEAD, ComponentKey.MOBILITY to Role.PROP, ComponentKey.SLEEP to Role.GUARD, ComponentKey.REST to Role.GUARD),
            listOf(c("подтягивания подряд", 1.0, 3.0, "", PathMetric.PULLUPS), c("силовые в неделю", 2.0, 3.0, "", PathMetric.STRENGTH_SESSIONS)),
            "в недели переработок силовые уходят первыми",
        ),
        GoalTemplate(
            RACE, "Забег", GoalKind.DO,
            listOf("забег", "марафон", "полумарафон", "бег", "пробеж", "км", "трейл"),
            listOf(ComponentKey.CARDIO to Role.LEAD, ComponentKey.MOBILITY to Role.GUARD, ComponentKey.REST to Role.PROP, ComponentKey.SLEEP to Role.GUARD),
            listOf(c("пробежки в неделю", 2.0, 3.0, "", PathMetric.RUNS), c("минуты в лёгких зонах за неделю", 90.0, 150.0, "мин", PathMetric.ZONE_MINUTES)),
            "после работы нет сил выйти",
        ),
        GoalTemplate(
            PROMOTION, "Повышение или новая роль", GoalKind.BE,
            listOf("повышен", "руковод", "должност", "роль", "карьер", "тимлид", "лидер", "отдел"),
            listOf(
                ComponentKey.MASTERY to Role.LEAD, ComponentKey.PEOPLE to Role.LEAD, ComponentKey.ATTENTION to Role.PROP,
                ComponentKey.CARDIO to Role.PROP, ComponentKey.SLEEP to Role.GUARD,
            ),
            listOf(c("фокус-блоки в неделю", 4.0, 6.0, "", PathMetric.FOCUS_BLOCKS), c("трудные разговоры в месяц", 2.0, 4.0, "", PathMetric.HARD_TALKS)),
            "после 22:00 отвечаю в рабочем чате",
        ),
        GoalTemplate(
            LANGUAGE, "Язык", GoalKind.DO,
            listOf("язык", "англ", "немец", "испан", "француз", "итальян", "китай", "english"),
            listOf(ComponentKey.MASTERY to Role.LEAD, ComponentKey.ATTENTION to Role.PROP, ComponentKey.CLARITY to Role.PROP, ComponentKey.SLEEP to Role.GUARD),
            listOf(c("занятия в неделю", 3.0, 5.0, "", PathMetric.SKILL_SESSIONS), c("минуты в неделю", 90.0, 150.0, "мин", PathMetric.SKILL_SESSIONS)),
            "откладываю на «когда будет время»",
        ),
        GoalTemplate(
            PROJECT, "Проект или книга к дате", GoalKind.DO,
            listOf("книг", "проект", "диссер", "запуст", "написать", "дописать", "стартап", "курс"),
            listOf(ComponentKey.ATTENTION to Role.LEAD, ComponentKey.CLARITY to Role.PROP, ComponentKey.REST to Role.GUARD, ComponentKey.SLEEP to Role.GUARD),
            listOf(c("фокус-блоки в неделю", 5.0, 8.0, "", PathMetric.FOCUS_BLOCKS), c("готовые части", 3.0, 5.0, "", PathMetric.MILESTONES)),
            "телефон рядом во время работы",
        ),
        GoalTemplate(
            ENERGY, "Силы на вечер и детей", GoalKind.HAVE,
            listOf("сил", "энерг", "дет", "вечер", "бодр", "устал"),
            listOf(ComponentKey.REST to Role.LEAD, ComponentKey.CARDIO to Role.LEAD, ComponentKey.PEOPLE to Role.PROP, ComponentKey.SLEEP to Role.GUARD),
            listOf(c("вечера без работы из 7", 3.0, 4.0, "", PathMetric.REST_EVENINGS), c("ночи от 7 ч из 7", 4.0, 5.0, "", PathMetric.SLEEP_NIGHTS)),
            "работа догоняет вечером",
        ),
        GoalTemplate(
            CALM, "Спокойнее в работе", GoalKind.BE,
            listOf("спокой", "стресс", "нерв", "напряж", "тише"),
            listOf(
                ComponentKey.REST to Role.LEAD, ComponentKey.OUTLOOK to Role.PROP, ComponentKey.ATTENTION to Role.PROP,
                ComponentKey.FEELINGS to Role.GUARD, ComponentKey.SLEEP to Role.GUARD,
            ),
            listOf(c("передышки в неделю", 3.0, 5.0, "", PathMetric.BREATHERS), c("вечера без работы из 7", 3.0, 4.0, "", PathMetric.REST_EVENINGS)),
            "мысли о работе не отпускают вечером",
            review = true,
        ),
        GoalTemplate(
            CLOSER, "Ближе с близкими", GoalKind.BE,
            listOf("близк", "семь", "друз", "отношен", "жен", "муж", "родител", "партн"),
            listOf(ComponentKey.PEOPLE to Role.LEAD, ComponentKey.ATTENTION to Role.PROP, ComponentKey.REST to Role.PROP, ComponentKey.FEELINGS to Role.GUARD),
            listOf(c("тёплые контакты в неделю", 3.0, 5.0, "", PathMetric.WARM_CONTACTS), c("вечера без экрана в неделю", 2.0, 3.0, "", PathMetric.REST_EVENINGS)),
            "вечером залипаю в телефон",
        ),
        GoalTemplate(
            STRONG, "Крепко после 50–60", GoalKind.BE,
            listOf("после 50", "после 60", "крепк", "50", "60", "возраст"),
            listOf(ComponentKey.STRENGTH to Role.LEAD, ComponentKey.MOBILITY to Role.LEAD, ComponentKey.CARDIO to Role.PROP, ComponentKey.SLEEP to Role.GUARD),
            listOf(c("силовые в неделю", 2.0, 3.0, "", PathMetric.STRENGTH_SESSIONS), c("занятия на баланс в неделю", 2.0, 3.0, "", PathMetric.BALANCE_SESSIONS)),
            "боюсь перестараться",
            review = true,
            parq = true,
        ),
    )

    fun byId(id: String?): GoalTemplate? = ALL.firstOrNull { it.id == id }

    /** The templates the ritual offers (Э1): weight and strength behind the flag, «Форма» closed in ed_care. */
    fun offered(flags: PathFlags, given: GivenLayer): List<GoalTemplate> = ALL.filter { t ->
        (flags.weightAndStrength || !t.weightOrStrength) && !(given.closesWeight && t.touchesWeight)
    }

    /**
     * R1: the template of a goal said in words, by a dictionary — no AI in numbers (A56 Э1). The
     * template with most keyword hits wins; a tie goes to the earlier template; no hit → null
     * («Соберём вместе»). Only [offered] templates take part.
     */
    fun match(text: String, flags: PathFlags = PathFlags.DEFAULT, given: GivenLayer = GivenLayer.NONE): GoalTemplate? {
        val words = PathWords.of(text)
        return offered(flags, given)
            .map { tpl -> tpl to tpl.keywords.count { words.has(it) } }
            .filter { it.second > 0 }
            .maxWithOrNull(compareBy<Pair<GoalTemplate, Int>> { it.second }.thenByDescending { ALL.indexOf(it.first) })
            ?.first
    }
}

/** What HoT says to a goal it does not take as it is (A56 §2.2, P-20). */
enum class GoalGate {
    OK,

    /** A goal on a value of an analysis or a disease: HoT offers a behaviour and «В вопросы к врачу». */
    MEDICAL,

    /** A harmful goal («спать 4 ч», fasting for days): a refusal without a moral, with an alternative. */
    HARMFUL,

    /** A weight goal in ed_care, pregnancy or medical flags: «Могу про силу или энергию». */
    WEIGHT_CLOSED,

    /** A weight or strength goal while the safe pace tables are missing (flag off). */
    WEIGHT_LATER,
}

/**
 * P-20 and A56 §2.2: what goal HoT does not create. A dictionary, deterministic. The crisis words
 * are not here: the screen runs CrisisGuard (A07) over every free answer before this.
 */
object GoalGuard {
    private val medical = listOf(
        "давлен", "ферритин", "холестерин", "сахар в крови", "уровень сахара", "глюкоз", "гемоглобин", "анализ",
        "таблет", "лекарств", "инсулин", "щитовид", "ттг", "гормон", "диагноз", "вылечить", "лечени", "холестер",
    )
    private val harmful = listOf(
        "спать 4", "спать по 4", "спать 3", "спать по 3", "спать меньше", "голодан", "не есть", "без еды",
        "на воде", "сушк", "обезвож",
    )
    private val weight = listOf("похуд", "сброс", "=вес", "=веса", "=весу", "=весом", "кг", "рельеф", "калори", "тали", "фигур", "жир")
    private val strength = listOf("подтяг", "жим", "присед", "=сила", "=силу", "сильнее", "отжим")

    fun check(text: String, given: GivenLayer = GivenLayer.NONE, flags: PathFlags = PathFlags.DEFAULT): GoalGate {
        val w = PathWords.of(text)
        if (medical.any(w::has)) return GoalGate.MEDICAL
        if (harmful.any(w::has)) return GoalGate.HARMFUL
        val isWeight = weight.any(w::has)
        if (isWeight && given.closesWeight) return GoalGate.WEIGHT_CLOSED
        if (!flags.weightAndStrength && (isWeight || strength.any(w::has))) return GoalGate.WEIGHT_LATER
        return GoalGate.OK
    }
}

/**
 * Words of a free answer for the dictionaries: lower case, «ё» as «е». A key with a space is a
 * phrase (found anywhere); a key «=слово» is a whole word; any other key is the start of a word
 * («подтяг» finds «подтянуться», but «вес» never finds «весна»).
 */
class PathWords private constructor(private val text: String, private val tokens: List<String>) {
    fun has(key: String): Boolean {
        val k = key.lowercase().replace('ё', 'е')
        return when {
            k.startsWith("=") -> tokens.any { it == k.drop(1) }
            k.contains(' ') -> text.contains(k)
            else -> tokens.any { it.startsWith(k) }
        }
    }

    companion object {
        fun of(text: String): PathWords {
            val t = text.lowercase().replace('ё', 'е')
            val tokens = t.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
            return PathWords(" " + tokens.joinToString(" ") + " ", tokens)
        }
    }
}
