package com.healthofatitan.app.feature.checkin

import com.healthofatitan.app.core.model.mentor.MentorPersona
import com.healthofatitan.app.domain.checkin.BodyArea
import com.healthofatitan.app.domain.checkin.BodyFeel
import com.healthofatitan.app.domain.checkin.CardId
import com.healthofatitan.app.domain.checkin.Drive
import com.healthofatitan.app.domain.checkin.Head
import com.healthofatitan.app.domain.checkin.Intimacy
import com.healthofatitan.app.domain.checkin.LineContext
import com.healthofatitan.app.domain.checkin.Link
import com.healthofatitan.app.domain.checkin.Mood
import com.healthofatitan.app.domain.checkin.Pull
import com.healthofatitan.app.domain.checkin.RitualItem
import com.healthofatitan.app.domain.checkin.Sky
import com.healthofatitan.app.domain.checkin.Yesterday
import java.util.Locale
import kotlin.math.abs

/**
 * Every word of the state cards (A44 v0.2, D-036) in one place.
 *
 * REVIEW: ⚑ Agent 10 (tone, «Только ты», the storm net, the mentor lines, the specialist route)
 * and ⚑ Agent 3 (body, pain, supplements, alcohol, sleep inertia, «связь, не причина»). Status:
 * draft for review — nothing here is final until both sign off (CLAUDE.md, A44 §6.6).
 *
 * Red lines kept: no diagnosis, no norms of frequency, no morals, never the word «зависимость»;
 * supplements are the fact of taking only, never a dose (veto №3 of Agent 3); the «быстрый
 * дофамин» thesis is not used anywhere; alcohol and «Только ты» get only the neutral «Принято».
 * The SOS screen texts (A07 §4.4) are not here and are not changed.
 */
object CheckinTexts {

    // ── Deck ───────────────────────────────────────────────────────────────────────────────
    const val WAKE = "Пробуждение"
    const val RITUAL = "После ритуала"
    const val QUICK = "Быстро"
    const val BACK = "назад"
    const val SKIP = "пропустить"
    const val TAP_HINT = "Тап — и Титан меняется сразу"
    const val NEXT = "Дальше"
    const val CLOSE = "Закрыть"
    const val DONE = "Готово"
    const val ACCEPTED = "Принято"

    fun progress(index: Int, total: Int): String = "$WAKE · ${index + 1} / $total"

    fun left(seconds: Int): String = "≈ $seconds с осталось"

    fun nextUp(cards: List<CardId>): String? =
        cards.takeIf { it.isNotEmpty() }?.joinToString(" · ", prefix = "Дальше: ") { short(it) }

    fun title(card: CardId): String = when (card) {
        CardId.CHARGE -> "Заряд"
        CardId.WEATHER -> "Погода внутри"
        CardId.BODY -> "Тело"
        CardId.HEAD -> "Голова"
        CardId.DRIVE -> "Тяга к делу"
        CardId.SLEPT -> "Как спалось"
        CardId.YESTERDAY -> "Вчера вечером"
        CardId.ONLY_YOU, CardId.INTIMACY, CardId.PULLS -> "Только ты"
        CardId.RITUAL -> "Утро как вчера?"
        CardId.CHARGE_AFTER -> "Заряд сейчас"
        CardId.EVENING_WEATHER -> "Погода вечера"
        CardId.EVENING_AREAS -> "Где?"
        CardId.WEEK_PEOPLE -> "Неделя с людьми"
        CardId.WEEK_CLOSENESS -> "Хватает ли тебе близости?"
    }

    fun short(card: CardId): String = when (card) {
        CardId.DRIVE -> "Тяга"
        CardId.YESTERDAY -> "Вчера"
        CardId.WEATHER -> "Погода"
        CardId.SLEPT -> "Сон"
        else -> title(card)
    }

    /** ⓘ «зачем»: which facet and which discoveries the card feeds (A44 §8). */
    fun why(card: CardId): String = when (card) {
        CardId.CHARGE -> "Заряд и «ножницы» с WHOOP; дельта ритуала"
        CardId.WEATHER -> "Погода дня; страховка, если штормит"
        CardId.BODY -> "Весы; «забито» облегчает тренировку на эту зону"
        CardId.HEAD -> "Ясность; открытия «стек → Голова»"
        CardId.DRIVE -> "Мягкое начало дня; открытия «ритуал → Тяга»"
        CardId.SLEPT -> "Только если WHOOP не видел ночь"
        CardId.YESTERDAY -> "Топливо и ритм; открытия «экраны → сон»"
        CardId.ONLY_YOU, CardId.INTIMACY, CardId.PULLS -> "Связи; только на телефоне"
        CardId.RITUAL -> "Главный источник открытий утра"
        CardId.CHARGE_AFTER -> "Дельта утра: что помогает"
        CardId.EVENING_WEATHER -> "Траектория дня и страховка"
        CardId.EVENING_AREAS -> "Зоны без нагрузки завтра"
        CardId.WEEK_PEOPLE -> "Связи, раз в неделю"
        CardId.WEEK_CLOSENESS -> "Удовлетворённость, не частота"
    }

    // ── Answers (pictures carry the meaning, the word is the label and the contentDescription) ──
    val CHARGE_LEVELS = listOf("на нуле", "мало", "средне", "бодро", "полон сил")

    fun charge(level: Int): String = CHARGE_LEVELS.getOrElse(level - 1) { "" }

    fun sky(s: Sky): String = when (s) {
        Sky.STORM -> "гроза"
        Sky.RAIN -> "дождь"
        Sky.CLOUDY -> "облачно"
        Sky.BREAK -> "просвет"
        Sky.CLEAR -> "ясно"
    }

    const val IN_WORDS = "+ словами"

    fun mood(m: Mood): String = when (m) {
        Mood.CALM -> "спокойно"
        Mood.GLAD -> "радостно"
        Mood.TIRED -> "устал"
        Mood.ANXIOUS -> "тревожно"
        Mood.IRRITATED -> "раздражение"
        Mood.EMPTY -> "пусто"
    }

    fun body(b: BodyFeel): String = when (b) {
        BodyFeel.LIGHT -> "легко"
        BodyFeel.SORE -> "ноет · забито"
        BodyFeel.SICK -> "простуда"
    }

    fun head(h: Head): String = when (h) {
        Head.FOG -> "туман"
        Head.MID -> "средне"
        Head.CLEAR -> "ясно"
    }

    fun drive(d: Drive): String = when (d) {
        Drive.NONE -> "нет сил"
        Drive.OK -> "норм"
        Drive.EAGER -> "рвусь"
    }

    fun yesterday(y: Yesterday): String = when (y) {
        Yesterday.SCREENS_LATE -> "экраны после 23"
        Yesterday.LATE_FOOD -> "поздняя еда"
        Yesterday.ALCOHOL -> "алкоголь"
        Yesterday.SWEETS -> "сладкое"
        Yesterday.COFFEE_LATE -> "кофе после 14"
        Yesterday.NOTHING -> "ничего из этого"
    }

    const val HOW_MANY = "Сколько?"
    val SERVINGS = listOf(1 to "1", 2 to "2", 3 to "3", 4 to "4+")

    val SLEPT_LEVELS = listOf("тяжело", "так себе", "средне", "хорошо", "отлично")

    fun ritual(item: RitualItem): String = when (item) {
        RitualItem.COFFEE -> "кофе"
        RitualItem.COLD_SHOWER -> "холодный душ"
        RitualItem.LIGHT -> "свет"
        RitualItem.BREATH -> "дыхание"
        RitualItem.WATER -> "вода"
    }

    const val AS_YESTERDAY = "Как вчера"
    const val STACK_HINT = "Добавки — из твоего стека, как ты их назвал. Только факт, без доз."
    const val ADD_TO_STACK = "Добавить в стек"
    const val STACK_NAME_HINT = "Название, как на банке"

    fun bodyArea(a: BodyArea): String = when (a) {
        BodyArea.NECK -> "шея"
        BodyArea.SHOULDERS -> "плечи"
        BodyArea.CHEST -> "грудь"
        BodyArea.ARMS -> "руки"
        BodyArea.BACK -> "спина"
        BodyArea.LOWER_BACK -> "поясница"
        BodyArea.HIPS -> "бёдра"
        BodyArea.KNEES -> "колени"
        BodyArea.CALVES -> "икры"
    }

    const val WEEK_SCALE_LOW = "мало"
    const val WEEK_SCALE_HIGH = "много"
    const val CLOSENESS_LOW = "не хватает"
    const val CLOSENESS_HIGH = "хватает"

    // ── «Только ты» (S5) — ⚑ Agent 10 veto ──────────────────────────────────────────────────
    const val ONLY_YOU = "Только ты"
    const val ONLY_YOU_LINE = "Видишь только ты. Только на телефоне, по отпечатку."
    const val CLOSENESS = "Близость"
    const val PULLS = "Что тянуло?"
    const val WANT_LESS = "хотелось меньше"
    const val WANT_LESS_HINT = "По желанию: если тебе самому это не нравится. Это не оценка."

    fun intimacy(i: Intimacy): String = when (i) {
        Intimacy.NONE -> "не было"
        Intimacy.PARTNER -> "с партнёром"
        Intimacy.SELF -> "сам с собой"
        Intimacy.NO_MARK -> "не отмечать"
    }

    /** The word stays in the contentDescription and the help, not on the button (A44 §6.4). */
    fun intimacySpoken(i: Intimacy): String = when (i) {
        Intimacy.SELF -> "сам с собой, мастурбация"
        else -> intimacy(i)
    }

    fun pull(p: Pull): String = when (p) {
        Pull.FEED -> "лента"
        Pull.VIDEO -> "видео"
        Pull.GAMES -> "игры"
        Pull.SWEETS -> "сладкое"
        Pull.SHOPPING -> "покупки"
        Pull.ADULT -> "18+"
        Pull.NOTHING -> "ничего"
    }

    const val UNLOCK_TITLE = "Только ты"
    const val UNLOCK_SUBTITLE = "Подтверди, что это ты"
    const val UNLOCK = "Открыть по отпечатку"
    const val LOCKED = "Под замком"

    const val OFFER_TITLE = "Слой «Только ты»"
    const val OFFER_LINE = "Близость и «что тянуло» — раз в утро, одним тапом. Без морали и норм. Видишь только ты: на телефоне, по отпечатку, никогда у наставника и на сервере."
    const val OFFER_YES = "Включить"
    const val OFFER_NO = "Не сейчас"

    const val SPECIALIST = "Если это мешает тебе, можно обсудить со специалистом — психотерапевтом или сексологом. Это обычная тема для них."
    const val SPECIALIST_OK = "Понятно"

    const val PRIVATE_OFF_ALL = "Выключить и удалить всё"
    const val PRIVATE_OFF_CONFIRM = "Все отметки «Только ты» и открытия под замком будут удалены с телефона. Вернуть их нельзя."
    const val PRIVATE_LINKS = "Замечать связи"
    const val PRIVATE_LINKS_WHY = "Открытия «близость → сон», «что тянуло → Тяга». Только под замком."

    // ── Reveal «ты ↔ WHOOP» ─────────────────────────────────────────────────────────────────
    const val YOU = "ты"
    const val WHOOP = "WHOOP"
    const val AGREE = "Сошлись"
    const val DIVERGE = "Разошлись"
    const val DIVERGE_LINE = "Ощущение не бывает неправильным. Разошлись — значит, есть что заметить."
    const val WAITING = "WHOOP досчитывает ночь — раскрою, когда придёт."
    const val NO_WHOOP = "WHOOP не подключён — сегодня только ты."
    const val RITUAL_LATER = "«После ритуала» спрошу при следующем открытии."
    const val TO_DAY = "К дню"

    fun matched(n: Int, m: Int): String = "совпали с WHOOP $n из $m"

    fun mornings(n: Int): String = "утра с тобой — $n из 30"

    fun levelSpoken(who: String, level: Int): String = "$who: Заряд ${charge(level)}"

    // ── After the ritual ────────────────────────────────────────────────────────────────────
    const val RITUAL_CARD = "Как сейчас?"
    const val RITUAL_CARD_TIME = "15 с"

    fun delta(from: String, fromLevel: Int, to: String, toLevel: Int): String =
        "$from ${charge(fromLevel)} → $to ${charge(toLevel)}"

    fun deltaLevels(d: Int): String = when {
        d > 0 -> "+$d"
        else -> d.toString()
    } + " " + levels(abs(d))

    /** ⚑ Agent 3 (§6.3, sleep inertia): honest about what the delta can and cannot say. */
    const val DELTA_NOTE = "Бодрость после сна растёт и сама — обычно за 15–60 минут. Что помогает именно тебе, покажет сравнение утр с ритуалом и без него."

    // ── Evening ─────────────────────────────────────────────────────────────────────────────
    const val ALREADY_KNOW = "Уже знаю — спрашивать не буду"

    fun alreadyKnow(items: List<String>): String = "$ALREADY_KNOW: ${items.joinToString(", ")}"

    const val KNOW_TRAINING = "тренировку"
    const val KNOW_STEPS = "шаги"
    const val KNOW_PATH = "шаг к цели"
    const val WHERE = "Где?"
    const val WHERE_HINT = "Тап по зоне. Завтра её не нагружаю."
    const val WEEK = "Вопрос недели"

    // ── Body: «Это боль» — ⚑ Agent 3 ────────────────────────────────────────────────────────
    const val IT_HURTS = "Это боль"
    const val IT_HURTS_HINT = "Не «забито», а боль — отдельно."
    const val PAIN_CHEST = "Давит или жжёт в груди"
    const val PAIN_JOINT = "Болит сустав"

    /** SAF-01 why_ru (content/rules/safety.yaml), unchanged wording; the number is the country's. */
    fun painChest(number: String) = "Ты отметил давление или жжение в груди. Если это происходит сейчас — звони $number. Тренировки на паузе, пока врач не разрешит."

    const val PAIN_JOINT_LINE = "Эту зону сегодня не нагружаю. Если боль острая, сустав отекает или болит в покое — покажись врачу."
    const val CALL = "Позвонить"

    // ── Storm net «Как ты на самом деле?» (sc6) — ⚑ Agent 10 ────────────────────────────────
    const val CARE_TITLE = "Как ты на самом деле?"
    const val CARE_LINE = "Погода последних отметок — гроза. Я рядом. Выбери, что сейчас ближе."
    const val CARE_HELP = "Нужна помощь сейчас"
    const val CARE_TALK = "Поговорить"
    const val CARE_OK = "Всё ок, просто день"
    const val CARE_OK_LINE = "Понял. Если что — помощь в один тап, под аватаром."

    // ── Discoveries (И2.21) — ⚑ Agents 10, 3, 9 ────────────────────────────────────────────
    const val DISCOVERY = "Открытие"
    const val LINK_NOT_CAUSE = "связь, не причина"
    const val HOW_COUNTED = "Как считал"
    const val COLLECTION = "Коллекция открытий"
    const val TO_COLLECTION = "В коллекцию"

    fun discoveryNumber(n: Int): String = "$DISCOVERY №$n"

    fun opened(n: Int, total: Int): String = "открыто $n из $total"

    fun leftMornings(n: Int): String = "ещё $n " + plural(n, "утро", "утра", "утр")

    fun pairTitle(p: Link): String = when (p) {
        Link.SHOWER_CHARGE -> "Холодный душ → Заряд"
        Link.LIGHT_CHARGE -> "Свет → Заряд"
        Link.BREATH_CHARGE -> "Дыхание → Заряд"
        Link.STACK_HEAD -> "Стек утра → Голова"
        Link.SCREENS_SLEEP -> "Экраны после 23 → сон"
        Link.SCREENS_CHARGE -> "Экраны после 23 → Заряд"
        Link.ALCOHOL_PULSE -> "Алкоголь → пульс покоя"
        Link.ALCOHOL_HEAD -> "Алкоголь → Голова"
        Link.SWEETS_WEATHER -> "Сладкое вечером → Погода"
        Link.TRAINING_HEAD -> "Тренировка → Голова завтра"
        Link.CLOSENESS_SLEEP -> "Близость → сон"
        Link.PULLS_DRIVE -> "Что тянуло → Тяга к делу"
    }

    /**
     * «После холодного душа Заряд к 9:00 выше на 1,4 уровня». Personal, past tense, with the
     * numbers; never «делай», never «полезно».
     */
    fun pairLine(p: Link, effect: Double): String {
        val up = effect > 0
        val n = String.format(Locale.forLanguageTag("ru"), "%.1f", abs(effect))
        val dir = if (up) "выше" else "ниже"
        return when (p) {
            Link.SHOWER_CHARGE -> if (up) "В утра с холодным душем Заряд вырос на $n ${levels(abs(effect))} больше" else "В утра с холодным душем Заряд рос на $n ${levels(abs(effect))} меньше"
            Link.LIGHT_CHARGE -> if (up) "В утра со светом Заряд вырос на $n ${levels(abs(effect))} больше" else "В утра со светом Заряд рос на $n ${levels(abs(effect))} меньше"
            Link.BREATH_CHARGE -> if (up) "В утра с дыханием Заряд вырос на $n ${levels(abs(effect))} больше" else "В утра с дыханием Заряд рос на $n ${levels(abs(effect))} меньше"
            Link.STACK_HEAD -> "На следующее утро после стека Голова $dir на $n ${levels(abs(effect))}"
            Link.SCREENS_SLEEP -> "После вечеров с экранами после 23 сон по WHOOP $dir твоей нормы на $n"
            Link.SCREENS_CHARGE -> "После вечеров с экранами после 23 Заряд утром $dir на $n ${levels(abs(effect))}"
            Link.ALCOHOL_PULSE -> "После вечеров с алкоголем пульс покоя $dir твоей нормы на $n"
            Link.ALCOHOL_HEAD -> "После вечеров с алкоголем Голова утром $dir на $n ${levels(abs(effect))}"
            Link.SWEETS_WEATHER -> "После сладкого вечером Погода утром $dir на $n ${levels(abs(effect))}"
            Link.TRAINING_HEAD -> "На утро после тренировки Голова $dir на $n ${levels(abs(effect))}"
            Link.CLOSENESS_SLEEP -> "После дней с близостью сон по WHOOP $dir твоей нормы на $n"
            Link.PULLS_DRIVE -> "В дни, когда что-то тянуло, Тяга к делу $dir на $n ${levels(abs(effect))}"
        }
    }

    fun counts(n1: Int, n0: Int): String = "$n1 и $n0 ${plural(n0, "утро", "утра", "утр")} · $LINK_NOT_CAUSE"

    /** ⚑ Agent 9: the method in plain words. */
    fun howCounted(n1: Int, n0: Int, lo: Double, hi: Double): String {
        val f = { x: Double -> String.format(Locale.forLanguageTag("ru"), "%.1f", x) }
        return "Сравнил медианы твоих утр «с» ($n1) и «без» ($n0) за 90 дней. Интервал разницы (бутстреп, 95 %): от ${f(lo)} до ${f(hi)} — он не пересекает ноль. " +
            "Это твоя личная связь, не причина: в эти утра могло отличаться и другое. Список пар задан заранее, ИИ не участвует."
    }

    const val GENTLE_DISCOVERY = "Мягкое наблюдение"

    // ── Settings «Что спрашивать» (sc7) ─────────────────────────────────────────────────────
    const val SETTINGS = "Что спрашивать"
    const val SETTINGS_CAPTION = "Карточки утра и вечера"
    const val S_WAKE = "Пробуждение"
    const val S_WAKE_CORE = "Заряд и Погода — всегда"
    const val S_QUICK = "Быстро по умолчанию"
    const val S_QUICK_WHY = "Только Заряд и Погода, ≈ 10 с"
    const val S_RITUAL = "После ритуала"
    const val S_RITUAL_WHY = "Стек утра и Заряд сейчас, ≈ 15 с"
    const val S_RITUAL_REMIND = "Напомнить через 45 минут"
    const val S_RITUAL_REMIND_WHY = "Выключено: отдельного пуша нет, карточка ждёт на «Сегодня» до 12:00"
    const val S_EVENING = "Вечер"
    const val S_EVENING_WHY = "Погода вечера и вопрос недели в «Закрыть день»"
    const val S_DRINKS = "Не пью"
    const val S_DRINKS_WHY = "Алкоголь не спрашиваю нигде"
    const val S_GAME = "Игра"
    const val S_DISCOVERIES = "Открытия и коллекция"
    const val S_DISCOVERIES_WHY = "Не чаще раза в неделю; связь, не причина"
    const val S_HAPTICS = "Вибрация"
    const val S_STACK = "Мой стек утра"
    const val S_STACK_EMPTY = "Пока пусто — добавь, что принимаешь утром"
    const val S_REMOVE = "Убрать"
    const val S_USAGE = "Как идёт"

    fun usage(wakeSec: Long?, ritualSec: Long?): String = listOfNotNull(
        wakeSec?.let { "пробуждение ≈ $it с" },
        ritualSec?.let { "после ритуала ≈ $it с" },
    ).joinToString(" · ").ifEmpty { "пока нет данных" }

    fun skipShare(card: CardId, share: Double): String = "${title(card)}: пропуск ${(share * 100).toInt()} %"

    fun removalOffer(card: CardId): String = "«${title(card)}» ты чаще пропускаешь, чем отвечаешь. Убрать её из утра?"

    const val KEEP = "Оставить"

    // ── Today cards ─────────────────────────────────────────────────────────────────────────
    const val START_WAKE = "Утро · ≈ 60 с"
    const val START_WAKE_LINE = "Сначала ты, потом WHOOP."
    const val REVEAL_READY = "WHOOP досчитал ночь"
    const val REVEAL_OPEN = "Раскрыть"
    const val NEW_DISCOVERY = "Новое открытие"

    // ── Tile, shortcut, help ────────────────────────────────────────────────────────────────
    const val TILE = "Утро"

    /** «1,4 уровня», «2 уровня», «1 уровень». */
    fun levels(x: Double): String = if (x == Math.floor(x)) levels(x.toInt()) else "уровня"

    fun levels(n: Int): String = plural(n, "уровень", "уровня", "уровней")

    fun plural(n: Int, one: String, few: String, many: String): String {
        val m100 = n % 100
        val m10 = n % 10
        return when {
            m100 in 11..14 -> many
            m10 == 1 -> one
            m10 in 2..4 -> few
            else -> many
        }
    }

    // ── Mentor: one offline line by character (И2.16) — ⚑ Agents 10, 16 ─────────────────────
    /** Never about alcohol or closeness; in Бережно only support (A44 §6.6). */
    fun line(persona: MentorPersona, ctx: LineContext): List<String> = when (ctx) {
        LineContext.NEUTRAL -> listOf(ACCEPTED)
        LineContext.GENTLE -> when (persona) {
            MentorPersona.BRO -> listOf("Сегодня по-тихому. Я рядом.", "Без спешки. Один шаг — и уже хорошо.")
            MentorPersona.BUTLER -> listOf("Сегодня бережно. Я всё подготовил полегче.", "Спокойный день — тоже день.")
            else -> listOf("Тихий день — тоже путь.", "Бережно. Дыхание, свет, малый шаг.")
        }
        LineContext.MATCH -> when (persona) {
            MentorPersona.BRO -> listOf("Ты и WHOOP на одной волне. Погнали.", "Чуйка не подвела — сходится.")
            MentorPersona.BUTLER -> listOf("Ваше ощущение и данные согласны. День собран.", "Сошлись. План — как задумано.")
            else -> listOf("Тело и чувство говорят одно. Слушай обоих.", "Сошлись. Иди ровно.")
        }
        LineContext.YOU_LOWER -> when (persona) {
            MentorPersona.BRO -> listOf("Цифры бодрые, а ты — нет. Это важно, учту.", "Выспался, а ощущение так себе — бывает. Начнём мягче.")
            MentorPersona.BUTLER -> listOf("Данные лучше самочувствия. Начало дня сделаю мягче.", "Отмечу расхождение: начнём спокойнее.")
            else -> listOf("Цифра выше ощущения. Ощущение тоже правда.", "Ножницы. Начни мягко — и посмотри, что поможет.")
        }
        LineContext.YOU_HIGHER -> when (persona) {
            MentorPersona.BRO -> listOf("Ты бодрее, чем думает WHOOP. Кайф — но не геройствуй.", "Огонь внутри, тело восстанавливается. Держи темп ровно.")
            MentorPersona.BUTLER -> listOf("Самочувствие опережает данные. Нагрузку оставлю как есть.", "Вы бодрее цифр. План не поднимаю — так надёжнее.")
            else -> listOf("Дух впереди тела. Пусть тело догонит.", "Ощущение сильнее цифры. Иди, но без рывка.")
        }
        LineContext.WAITING -> listOf(WAITING)
        LineContext.NO_WHOOP -> listOf(NO_WHOOP)
        LineContext.QUICK -> when (persona) {
            MentorPersona.BRO -> listOf("Быстро и чётко. Принято.")
            MentorPersona.BUTLER -> listOf("Благодарю. Отмечено.")
            else -> listOf("Коротко — тоже честно.")
        }
        LineContext.RITUAL_UP -> when (persona) {
            MentorPersona.BRO -> listOf("Разогнался! Запомню это утро.")
            MentorPersona.BUTLER -> listOf("Заряд вырос. Отмечу утро в журнале.")
            else -> listOf("Свет поднялся. Утро запомнено.")
        }
        LineContext.RITUAL_SAME -> when (persona) {
            MentorPersona.BRO -> listOf("Ровно — тоже норм. Сравним утра потом.")
            MentorPersona.BUTLER -> listOf("Без изменений. Сравним с другими утрами.")
            else -> listOf("Ровная вода. Сравним утра позже.")
        }
        LineContext.EVENING_LIGHT -> when (persona) {
            MentorPersona.BRO -> listOf("Хороший вечер. До завтра.")
            MentorPersona.BUTLER -> listOf("Вечер отмечен. Доброй ночи.")
            else -> listOf("День закрыт. Отдыхай.")
        }
        LineContext.EVENING_HEAVY -> when (persona) {
            MentorPersona.BRO -> listOf("Тяжёлый день. Завтра начнём мягче.")
            MentorPersona.BUTLER -> listOf("Непростой вечер. Утро подготовлю бережнее.")
            else -> listOf("Буря проходит. Завтра — мягкое начало.")
        }
    }

    /** «Твой ритуал работает» — only after a discovery of the ritual (A44 §5). */
    fun discoveryLine(persona: MentorPersona): String = when (persona) {
        MentorPersona.BRO -> "Это твоё, не из статьи. Связь, а не приговор."
        MentorPersona.BUTLER -> "Ваша личная закономерность. Связь, не причина."
        else -> "Ты заметил своё. Связь, не причина."
    }
}
