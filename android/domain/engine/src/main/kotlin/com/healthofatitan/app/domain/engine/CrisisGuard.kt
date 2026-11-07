package com.healthofatitan.app.domain.engine

/**
 * Risk levels of the crisis protocol (A07 §4.3). The product's actions per level live in the app;
 * here only what the level means for the flow, never any wording (texts: A07 §4.4, Agent 10).
 */
enum class CrisisLevel(val value: Int) {
    /** 0 — no signs: normal work. */
    NONE(0),

    /** 1 — distress or an ambiguous phrase: help stays visible, one gentle check (A07 §4.3). */
    CLARIFY(1),

    /** 2 — passive thoughts: coaching stops, the 988 card is shown full screen. */
    PASSIVE(2),

    /** 3 — active or acute risk, or harm to another person: 911 and 988 on the first screen. */
    ACUTE(3),
    ;

    /** Levels 2–3 open the crisis card by themselves, with or without a network (A07 §4.1, D-029). */
    val autoShowHelp: Boolean get() = value >= PASSIVE.value

    /** Levels 2–3 stop «Работа» and coaching (A07 §4.3). */
    val stopsCoaching: Boolean get() = value >= PASSIVE.value

    companion object {
        fun of(value: Int): CrisisLevel = entries.firstOrNull { it.value == value } ?: if (value > 3) ACUTE else NONE
    }
}

/** Why the filter set the level; stored with the safety event instead of any text (A07 §4.7). */
enum class CrisisReason {
    /** Direct words about not wanting to live, suicide (A07 §4.2 «прямые»). */
    DIRECT,

    /** Indirect words: «я обуза», «всем будет проще без меня» (A07 §4.2 «косвенные»). */
    INDIRECT,

    /** Intent with means or time, a method or lethality question, self-harm that already happened. */
    MEANS,

    /** A threat to another person (A07 T12). */
    HARM_OTHERS,

    /** Distress or an ambiguous phrase, including hyperbole tied to a condition (A07 T7, T10). */
    AMBIGUOUS,

    /** The person mentions the topic to ask not to talk about it (A07 T13). */
    META,

    /** «Есть» on the safety question of SOS or the evening check-in. */
    ANSWER,

    /** Seven low evenings in a row (A07 T14, A04 §3). */
    PATTERN,
}

/** Where the words came from (A07 §4: every mode). Stored with the event, never the text. */
enum class CrisisSource { EVENING, MENTOR, VOICE, SOS, JOURNAL }

data class CrisisAssessment(val level: CrisisLevel, val reason: CrisisReason? = null) {
    companion object {
        val NONE = CrisisAssessment(CrisisLevel.NONE)
    }
}

/** The safety question of SOS and the evening check-in (A07 §3.2 step 3). */
enum class SafetyAnswer { NO, YES, DECLINE }

/**
 * CrisisGuard — the on-device layer of the crisis protocol (A07 §4.1 layer 1): a dictionary of
 * Russian and English triggers including slang, the idioms that are not a crisis, and the rules on
 * answers and patterns. No network, no LLM, no I/O: it works offline (T15) and gives the same
 * level for the same words. The model layer may only raise the level ([merge]).
 *
 * The dictionary follows A07 §4.2 and the cases T1–T15 of A07 §4.8; a change of the dictionary
 * is reviewed by Agent 10 and tested against T1–T15 on every release (CI runs these tests).
 */
object CrisisGuard {

    private class Trigger(val level: CrisisLevel, val reason: CrisisReason, val pattern: Regex)

    /** Unicode word classes: `\w` and `\b` must match Cyrillic. */
    private fun rx(pattern: String): Regex = Regex("(?U)$pattern")

    private fun t(level: CrisisLevel, reason: CrisisReason, vararg patterns: String): List<Trigger> =
        patterns.map { Trigger(level, reason, rx(it)) }

    /** Phrases that are not a crisis (A07 §4.2 last row). They are cut out before matching. */
    private val idioms: List<Regex> = listOf(
        "убил[аи]? бы",
        "убью (этот|эту|это|этого) \\w+",
        "убить (время|вечер|пару часов|час)",
        "(умираю|умру|умер|умерла|помираю) со смеху",
        "(умираю|умру) от (смеха|скуки|любопытства)",
        "застрелиться (с|от) (этим|этой|этих|этого)? ?\\w+",
        "чуть не умер(ла)?",
        "dying (of|from) laughter",
        "killing it",
        "kill (time|the deadline|this deadline)",
    ).map(::rx)

    /** «Не спрашивай про суицид» (T13): the topic is named to set a boundary, not as a thought. */
    private val meta: List<Regex> = listOf(
        "(не|не надо|хватит) (спрашивай|спрашивать|говори|говорить|напоминай|пиши|писать) (\\w+ )?(про|о|об|насчет) (суицид\\w*|самоубийств\\w*|смерт\\w*)",
        "(do not|dont|don t|stop) (ask|asking|talk|talking) (me )?about (suicide|dying|death)",
    ).map(::rx)

    /** Hyperbole tied to a work condition (T10 «Выпилюсь, если не закрою квартал») — ask, do not escalate. */
    private val conditional: List<Regex> = listOf(
        "(выпилюсь|застрелюсь|повешусь|удавлюсь|убьюсь) (\\w+ )?(если|когда|пока|из за)",
    ).map(::rx)

    private val triggers: List<Trigger> =
        // Level 3: intent with means or time, lethality, self-harm already done, vehicles, others.
        t(
            CrisisLevel.ACUTE, CrisisReason.MEANS,
            "сколько \\w* ?(таблет\\w*|капель|снотворн\\w*|\\w+) (\\w+ )?(смертельн\\w*|чтобы умереть|чтобы не проснуться|убьет)",
            "(смертельн\\w*|летальн\\w*) доз\\w*",
            "как (лучше |быстро |безболезненно )?(повеситься|покончить с собой|убить себя|отравиться|умереть)",
            "(приму|выпью|наглотаюсь|сожру|проглочу) (\\w+ )?(все|всю|пачку|горсть|кучу) (\\w+ )?(таблет\\w*|снотворн\\w*|лекарств\\w*)",
            "(таблетки|таблеток|снотворное|лекарства) (которые|что) (я )?(копил|копила|собрал|собрала|накопил|накопила)",
            "(накопил|накопила|собрал|собрала) (\\w+ )?(таблетки|таблеток|снотворное)",
            "(наглотался|наглоталась|принял все|приняла все|выпил все|выпила все) (\\w+ )?(таблет\\w*|снотворн\\w*|лекарств\\w*)",
            "(принял|приняла|выпил|выпила) (много |кучу |пачку |горсть |целую пачку )(таблет\\w*|снотворн\\w*)",
            "(уже )?(порезал|порезала|порезался|порезалась|изрезал|изрезала) себ\\w*",
            "кровь не (останавливается|остановить|останавливаеться)",
            "(въехать|въеду|врезаться|врежусь|влететь|влечу|разбиться|разобьюсь) (\\w+ )?(в|об) (столб|стену|дерево|отбойник|грузовик|фуру)",
            "(прыгнуть|прыгну|шагнуть|шагну|спрыгнуть|спрыгну|броситься|брошусь) (\\w+ )?(с|из|под) (крыши|моста|окна|балкона|поезд|машину|колеса)",
            "(сегодня|сейчас|этой ночью|завтра|прямо сейчас) (\\w+ ){0,3}(покончу с собой|убью себя|повешусь|наглотаюсь|вскрою вены|прыгну)",
            "(покончу с собой|убью себя|повешусь|вскрою вены) (\\w+ ){0,3}(сегодня|сейчас|этой ночью|завтра)",
            "у меня есть (ружье|пистолет|оружие|веревка|таблетки) (\\w+ ){0,3}(чтобы|и я|для)",
            "(kill myself|end my life|end it) (tonight|today|now|right now)",
            "(took|swallowed|taken) (all |a lot of |a bunch of |the whole )(my |the )?(pills|meds|tablets)",
            "how many (pills|tablets) (\\w+ ){0,3}(kill|lethal|to die|overdose)",
            "lethal dose",
        ) +
            t(
                CrisisLevel.ACUTE, CrisisReason.HARM_OTHERS,
                "(хочу|собираюсь|пойду|готов|готова|сейчас) (\\w+ ){0,2}(ударить|убить|зарезать|пырнуть|прирезать|застрелить|задушить|избить) (\\w+ ){0,2}(нож\\w*|пистолет\\w*|ружь\\w*|топор\\w*|молотк\\w*)",
                "(хочу|собираюсь|пойду|готов|готова) (\\w+ ){0,2}(убить|зарезать|пырнуть|прирезать|застрелить|задушить) (коллег\\w*|начальник\\w*|шеф\\w*|жен\\w*|муж\\w*|сосед\\w*|его|ее|их|человек\\w*|людей|мать|отца|ребенк\\w*|детей)",
                "(i want to|i m going to|i will) (kill|stab|shoot|hurt) (him|her|them|my \\w+|someone|people)",
            ) +
            // Level 2: direct words (A07 §4.2) and passive thoughts without a plan.
            t(
                CrisisLevel.PASSIVE, CrisisReason.DIRECT,
                "не (хочу|хочется|хотелось бы) (\\w+ )?жить",
                "жить (\\w+ )?не (хочу|хочется)",
                "не могу (\\w+ )?больше жить",
                "(?<!не )(хочу|хочется|хотелось бы) (\\w+ )?умереть",
                "(мне )?лучше (бы )?умереть",
                "лучше бы я (умер|умерла)",
                "покончить с собой|покончу с собой",
                "убить себя|убью себя|убила бы себя|убил бы себя",
                "суицид\\w*|самоубийств\\w*",
                "повеситься|повешусь",
                "вскрыть вены|вскрою вены",
                "наглотаться (\\w+ )?(таблет\\w*|снотворн\\w*)",
                "выпилиться|выпилюсь|роскомнадзорнуться|роскомнадзорнусь",
                "(режу|резать|порезать|поцарапать) себя|наношу себе",
                "незачем (\\w+ )?жить|зачем (мне )?(\\w+ )?жить",
                "нет смысла (\\w+ )?жить",
                "(?<!don t )(?<!do not )(want to|wanna) die",
                "kill myself|killing myself|\\bkms\\b|suicid\\w*|unalive",
                "end it all|don t want to (live|be alive)|do not want to (live|be alive)",
                "better off (dead|without me)",
            ) +
            t(
                CrisisLevel.PASSIVE, CrisisReason.INDIRECT,
                "лучше бы (я )?не (проснулся|проснулась|просыпался|просыпалась|родился|родилась)",
                "лучше бы меня не было",
                "(всем|всем вокруг|им|близким) (\\w+ )?(будет|было бы|стало бы|станет) (\\w+ )?(проще|легче|лучше) без меня",
                "я (\\w+ )?обуза",
                "нет смысла (\\w+ )?дальше",
                "everyone would be better off",
                "i m a burden|i am a burden",
            ) +
            // Level 1: distress without words about death, or an ambiguous phrase (A07 §4.3).
            t(
                CrisisLevel.CLARIFY, CrisisReason.AMBIGUOUS,
                "(хочу|хочется) (\\w+ )?исчезнуть|исчезнуть бы",
                "скоро (это|все) закончится",
                "(хочу|пора|надо) (\\w+ )?(со всеми )?попрощаться",
                "я (все|уже все) решил|я (все|уже все) решила",
                "(сил|сил моих) (больше )?нет|больше не могу|не вывожу",
                "невыносимо|пустота внутри|никому не нужен|никому не нужна",
                "раздаю (свои )?вещи",
                "want to disappear|can t go on|cannot go on|no way out",
            )

    /** Lowercase, «ё» → «е», apostrophes and punctuation to spaces, «нехочу» → «не хочу». */
    fun normalize(text: String): String =
        text.lowercase()
            .replace('ё', 'е')
            .replace(rx("[^\\p{L}\\p{Nd}]+"), " ")
            .replace(rx("\\bне(хочу|хочется|могу)\\b"), "не $1")
            .trim()

    /**
     * The level of one message, voice transcript or evening note. The maximum over all triggers
     * wins; idioms are cut out first, a boundary («не спрашивай про суицид») and work hyperbole
     * set level 1 unless other words in the same text are stronger.
     */
    fun assess(text: String): CrisisAssessment {
        var s = " ${normalize(text)} "
        if (s.isBlank()) return CrisisAssessment.NONE
        var floor = CrisisAssessment.NONE
        for (r in meta) {
            if (r.containsMatchIn(s)) {
                s = r.replace(s, " ")
                floor = CrisisAssessment(CrisisLevel.CLARIFY, CrisisReason.META)
            }
        }
        for (r in conditional) {
            if (r.containsMatchIn(s)) {
                s = r.replace(s, " ")
                floor = CrisisAssessment(CrisisLevel.CLARIFY, CrisisReason.AMBIGUOUS)
            }
        }
        for (r in idioms) s = r.replace(s, " ")
        val hit = triggers.filter { it.pattern.containsMatchIn(s) }.maxByOrNull { it.level.value }
        return if (hit != null && hit.level.value > floor.level.value) CrisisAssessment(hit.level, hit.reason) else floor
    }

    /** A07 §4.1: the device filter and the model; the maximum acts, the model never lowers it. */
    fun merge(device: CrisisLevel, model: CrisisLevel?): CrisisLevel =
        if (model != null && model.value > device.value) model else device

    /** The safety question (A07 §3.2): «Есть» → not below 2; «Не хочу отвечать» → 1. */
    fun answer(answer: SafetyAnswer): CrisisAssessment = when (answer) {
        SafetyAnswer.NO -> CrisisAssessment.NONE
        SafetyAnswer.YES -> CrisisAssessment(CrisisLevel.PASSIVE, CrisisReason.ANSWER)
        SafetyAnswer.DECLINE -> CrisisAssessment(CrisisLevel.CLARIFY, CrisisReason.ANSWER)
    }

    /** Evenings in a row with a low mood that make a pattern (A07 T14, A04 §3: ≥ 7 days). */
    const val LOW_MOOD_DAYS = 7

    /** Mood on the evening scale 1–10: «≤ 3» is low (A07 T14). */
    const val LOW_MOOD_MAX = 3

    /**
     * A07 T14: the last [LOW_MOOD_DAYS] evenings (oldest first) all answered and all low → level 1:
     * a gentle question and the advice to see a specialist, never a streak or a reproach.
     * A skipped evening (null) breaks the pattern: silence is not a low mood.
     */
    fun moodPattern(moods: List<Int?>): CrisisAssessment {
        val last = moods.takeLast(LOW_MOOD_DAYS)
        val low = last.size == LOW_MOOD_DAYS && last.all { it != null && it <= LOW_MOOD_MAX }
        return if (low) CrisisAssessment(CrisisLevel.CLARIFY, CrisisReason.PATTERN) else CrisisAssessment.NONE
    }
}
