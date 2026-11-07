package com.healthofatitan.app.domain.path

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Every word of «Путь» v1 the person reads, in one place (names of elements, components, roles
 * and marks — in [PathLexicon]).
 *
 * **Черновик.** Тексты пауз, отказов и «Перекрёстка» ждут ревью Агентов 10 (психика), 3 (тело,
 * сроки) и 7 (юридическое) — [REVIEW]. No diagnosis, no prescription; what a number means is said
 * by its source. The stop dictionary of A39 and P-19 is run over [all] in tests.
 */
object PathCopy {
    const val REVIEW = "черновик · ждёт ревью 10, 3, 7"

    /** P-19: words that never appear in «Путь» (with the A39 dictionary in the feature test). */
    val FORBIDDEN: List<String> = listOf("провал", "не успел", "пропуск", "серия", "серии", "отстаёшь", "отстаешь", "бросил")

    private val DAY_MONTH = DateTimeFormatter.ofPattern("dd.MM")

    fun date(d: LocalDate): String = d.format(DAY_MONTH)

    // Empty path (A56 §2.1 «Путь без цели»).
    const val EMPTY_TITLE = "Путь ждёт цели"
    const val EMPTY_LINE = "Опоры дня держу я."
    const val NAME_GOAL = "Назови цель"
    const val FROM_CARRYING = "Из того, что несёшь"

    // Path screen (A55 §1).
    const val OVERLINE = "Путь"
    const val WHY_EMPTY = "Зачем — по желанию, одной строкой"
    const val SLICE_A11Y = "Срез мира: Небо, Земля, Вода, Титан горизонта"
    const val GIVEN_EMPTY = "Дано: пока ничего"
    const val STAGE_OVERLINE = "Этап · 12 недель"
    const val MILESTONES = "Вехи и замеры"
    const val BALANCE_EVEN = "Ровно"
    const val BALANCE_EVEN_SUB = "Все три стихии не ниже порога."
    const val HEAVY_TITLE = "Неделя на восстановление"
    const val HEAVY_SUB = "Тоже часть пути. Всё взятое остаётся за тобой."
    const val PAUSED_TITLE = "Путь подождёт столько, сколько нужно."
    const val PAUSED_SUB = "Путь на паузе · опоры держу"
    const val HELD_TITLE = "Сейчас главное — ты."
    const val HELD_SUB = "Путь стоит, ничего не сгорает. Помощь рядом."
    const val STATUS_LEADS = "ведёт цель"
    const val STATUS_PROPS = "подпирает"
    const val STATUS_HOLDS = "держим"
    const val STATUS_KEPT = "взятое при тебе"
    const val OUTSIDE = "вне этой цели"
    const val PRACTICE = "практика, без шкалы"
    const val MARK_UNKNOWN = "рубеж узнаем на замере"
    const val LEVER_BADGE = "рычаг"
    const val SAFE_BADGE = "безопасная версия"
    const val MENU = "Цель"
    const val MOVE_DEADLINE = "Сдвинуть срок"
    const val SWITCH_MODE = "К сроку / целиком"
    const val PAUSE = "Пауза"
    const val RESUME = "Вернуться"
    const val RELEASE = "Отпустить"
    const val HISTORY = "История цели"
    const val MARK_TAKEN_ACTION = "Отметить рубеж"
    const val MARK_PICK = "Какой рубеж взят по замеру?"

    fun stage(n: Int, total: Int): String = "этап $n из $total"

    fun week(n: Int): String = "неделя $n из ${StageRules.WEEKS}"

    fun toMilestone(days: Long): String = if (days <= 0L) "Встреча на вехе сегодня" else "Встреча на вехе через $days дн."

    fun deadline(d: LocalDate): String = "к ${date(d)}"

    fun lever(name: String): String = "Рычаг сейчас — ${name.lowercase()}"

    fun leverSub(name: String): String = "${name} ниже порога. Шаг недели — сюда, остальное держим."

    fun leverStatus(name: String): String = "рычаг: ${name.lowercase()}"

    fun markLine(start: Int?, target: Int?): String = when {
        target == null && start == null -> MARK_UNKNOWN
        target == null -> "рубеж: ${PathLexicon.mark(start!!)}"
        else -> "рубеж: ${PathLexicon.mark(start ?: 0)} → ${PathLexicon.mark(target)}"
    }

    fun more(n: Int, element: String): String = "ещё $n в стихии «$element» · $OUTSIDE"

    fun markTaken(mark: Int): String = "Рубеж «${PathLexicon.mark(mark)}» взят. Теперь он за плечами."

    // Titan of the horizon (A57 §4).
    fun horizonName(beName: String?, deadline: LocalDate): String =
        beName?.trim()?.takeIf { it.isNotEmpty() } ?: "Ты к ${date(deadline)}"

    fun horizonLine(step: Int): String = "${PathLexicon.HORIZON_TITAN} · шаг $step из ${HorizonRules.STEPS}"

    const val HORIZON_SEATED = "Титан горизонта ждёт на камне · пауза не сгорает"
    const val HORIZON_MEETING = "Встреча: фигуры сходятся"
    const val HORIZON_RULE = "Шаг навстречу — каждое воскресенье. Взятый рубеж — огонь с него на тебя."
    const val FIRE_WITH_YOU = "огонь при тебе"
    const val FIRE_ON_HORIZON = "огонь на горизонте"
    const val FIRE_SHARED = "уже с тобой"

    // TalkBack of «Срез мира» (A58-VP §1.6). Черновик, ревью 10. A colour is never named.
    const val A11Y_OPEN = "Открыть составляющую"
    const val A11Y_OUTSIDE = "Вне этой цели."
    const val A11Y_PRACTICE = "Практика без шкалы."
    const val A11Y_PAUSED = "Цель на паузе. Взятое при тебе."
    const val A11Y_EXCLUDED = "Под защитой «Дано»."
    const val A11Y_WHY_EMPTY = "Зачем — не задано."

    fun a11yPart(part: SlicePart, leverDetail: String?, paused: Boolean): String = buildString {
        append(PathLexicon.name(part.key)).append(". ").append(PathLexicon.element(part.key.element)).append(". ")
        when (part.state) {
            PartState.OFF -> append(A11Y_OUTSIDE)
            PartState.EXCLUDED -> append(A11Y_EXCLUDED)
            PartState.PRACTICE -> append(cap(PathLexicon.role(part.role ?: Role.GUARD))).append(". ").append(A11Y_PRACTICE)
            PartState.MARKS -> {
                append(cap(PathLexicon.role(part.role ?: Role.GUARD))).append(". ")
                append("Рубеж «").append(PathLexicon.mark(part.mark)).append("»")
                part.target?.let { append(", цель — «").append(PathLexicon.mark(it)).append("»") }
                append(".")
            }
        }
        if (part.lever) append(" Рычаг сейчас").append(leverDetail?.let { ": $it" } ?: "").append(".")
        part.safeVersion?.let { append(" Безопасная версия: ").append(it).append(".") }
        if (paused && part.role != null) append(" ").append(A11Y_PAUSED)
    }

    fun a11yWhy(why: String?): String = why?.trim()?.takeIf { it.isNotEmpty() }?.let { "${PathLexicon.WHY}: $it" } ?: A11Y_WHY_EMPTY

    fun a11yGiven(lines: List<String>): String =
        if (lines.isEmpty()) GIVEN_EMPTY else "${PathLexicon.GIVEN}: ${lines.joinToString(", ")}."

    fun a11yHorizon(h: HorizonTitan, lightOnly: Boolean): String = buildString {
        append(if (lightOnly) "Свет горизонта" else PathLexicon.HORIZON_TITAN).append(", «").append(h.name).append("». ")
        append("Шаг ${h.step} из ${HorizonRules.STEPS}.")
        if (h.seated) append(" Ждёт на камне.")
        if (!lightOnly) {
            val on = h.fires.filter { it.light == HorizonLight.ON_HORIZON }.map { PathLexicon.name(it.key) }
            val with = h.fires.filter { it.light != HorizonLight.ON_HORIZON }.map { PathLexicon.name(it.key) }
            if (on.isNotEmpty()) append(" Его огни: ").append(on.joinToString(", ")).append(".")
            if (with.isNotEmpty()) append(" Уже с тобой: ").append(with.joinToString(", ")).append(".")
        }
    }

    fun a11ySlice(title: String?, balance: Balance): String =
        "Срез мира." + (title?.let { " Цель «$it»." } ?: "") + " Равновесие: ${balance.title}."

    fun a11yFireMoved(mark: Int, key: ComponentKey): String =
        "Рубеж «${PathLexicon.mark(mark)}» взят. Огонь: ${PathLexicon.name(key).lowercase()} — теперь у тебя."

    private fun cap(s: String) = s.replaceFirstChar { it.uppercase() }

    // Ritual «Назови цель» (A56 §2, 7 screens).
    const val SKIP = "Пропустить"
    const val HELP = "Помощь"
    const val BACK = "Назад"
    val CTA = listOf("Дальше", "Дальше", "Пусть идёт ко мне", "Так и есть", "Беру план", "Выбираю", "Беру путь")

    const val E1_TITLE = "Назови цель"
    const val E1_SUB = "Что важно для твоей жизни — к дате или к концу года?"
    const val E1_OWN = "Своими словами"
    const val E1_HINT = "Например: полумарафон весной"
    const val E1_CUSTOM = "Соберём вместе"

    fun e1Carrying(n: Int): String = "Из того, что несёшь · $n"

    fun e1FromSleep(hours: String): String = "Сон $hours ч — хочешь про силы на вечер?"

    const val E2_TITLE = "Что будет правдой к сроку?"
    const val E2_WHY = "Ради чего тебе это?"
    const val E2_WHY_HINT = "Одна строка — для себя. Можно пропустить."
    const val E2_DEADLINE = "Срок"
    val E2_DEADLINES = listOf("12 недель", "к лету", "к 31.12", "через год")
    const val E2_WEEK_SIZED = "Это размер недели — отправлю в Неделю."
    const val E2_TOO_FAR = "Больше двух лет — возьмём срок до двух лет, дальше этапами."

    const val E3_TITLE = "Кем ты будешь к сроку?"
    const val E3_SUB = "Назови себя одной фразой. Этот Титан встанет на горизонте и будет идти к тебе."
    const val E3_HINT = "Например: тот, у кого есть силы на лето"

    fun e3Default(deadline: LocalDate): String = "Без фразы он будет просто «${horizonName(null, deadline)}»."

    const val E4_TITLE = "Вот из чего сложится цель"
    const val E4_SUB = "Тап по детали — роль, убрать или заменить."
    const val E4_ADD = "+ из стихии"
    const val E4_REMOVE = "Убрать"
    const val E4_WATER_KEEP = "Вода остаётся в каждой цели: её можно заменить, не убрать."

    fun e4Lever(name: String, detail: String?): String = lever(name) + (detail?.let { ": $it" } ?: "") + "."

    fun e4Given(label: String): String = "$label — бережём."

    fun e4Start(since: String): String = "Стартуем с того, что уже взято: $since."

    const val E5_TITLE = "Что внутри тебя скорее всего помешает?"
    const val E5_FROM_CARRYING = "Из того, что ты несёшь:"
    const val E5_OWN = "своё слово"
    const val E5_IF_THEN = "Если — то"

    fun e5IfThen(obstacle: String): String = "Если $obstacle, то …"

    const val E5_FIRST_WEEK = "Это станет первой целью недели."

    const val E6_TITLE = "Как реально"
    const val E6_ON_TRACK = "Успеваем в твоём темпе."
    const val E6_NO_PACE = "Темп узнаем на вехе 1. Пока цель держится на привычках — они видны сразу."
    const val E6_PROCESS_ONLY = "К этому сроку честнее цель-привычка. Её и возьмём."
    const val E6_NOT_CEILING = "Это темп, а не потолок — на вехе сверим."
    const val E6_BY_DATE = "К СРОКУ"
    const val E6_FULL = "ЦЕЛИКОМ"
    const val E6_BY_DATE_SUB = "Держим дату, планка по темпу"
    const val E6_FULL_SUB = "Держим планку, срок расчётный"

    fun e6ByDate(deadline: LocalDate, range: String): String = "К ${date(deadline)}: $range"

    fun e6Full(range: String, by: LocalDate): String = "$range — к ${date(by)}"

    const val E7_COMING = "Уже идёт к тебе"
    const val E7_LETTER = "Написать письмо с горизонта (вскроется на вехе 8)"
    const val E7_LETTER_HINT = "Две-три строки от будущего себя. Только поддержка."

    fun e7Trail(m4: LocalDate, m8: LocalDate, meet: LocalDate): String =
        "12 недель · вехи: ${date(m4)}, ${date(m8)} · Встреча: ${date(meet)}"

    fun e7FirstFire(name: String): String = "Первый огонь уже твой: ${name.lowercase()}."

    // Gates (A56 §2.2, P-20). Черновик, ревью 3, 10, 7.
    const val GATE_MEDICAL = "На значение анализа цель не ставлю. Могу про привычку — например, 150 минут ходьбы в неделю, если врач одобрит. Вопрос положу в «К визиту»."
    const val GATE_HARMFUL = "Такую цель не возьму: она бьёт по телу. Могу про силы и сон — давай так."
    const val GATE_WEIGHT_CLOSED = "Цель про вес сейчас не возьму. Могу про энергию или выносливость."
    const val GATE_WEIGHT_LATER = "Цели по весу и силе подключу, когда будут таблицы безопасного темпа. Сейчас могу про выносливость или энергию."

    fun gate(g: GoalGate): String? = when (g) {
        GoalGate.OK -> null
        GoalGate.MEDICAL -> GATE_MEDICAL
        GoalGate.HARMFUL -> GATE_HARMFUL
        GoalGate.WEIGHT_CLOSED -> GATE_WEIGHT_CLOSED
        GoalGate.WEIGHT_LATER -> GATE_WEIGHT_LATER
    }

    /** One line per broken rule (Э4: «деталь не встаёт, строка с причиной»). */
    fun violation(v: Violation): String = when (v) {
        Violation.TOO_MANY_GOALS -> "Сейчас у пути одна цель. Новую начнём, когда эта будет взята или отпущена."
        Violation.TOO_MANY_DRAFTS -> "Один черновик уже ждёт — продолжим его."
        Violation.TOO_FEW_COMPONENTS -> "В цели хотя бы две детали."
        Violation.TOO_MANY_COMPONENTS -> "Больше шести деталей — уже не одна цель."
        Violation.NO_LEAD -> "Пусть хотя бы одна деталь ведёт."
        Violation.TOO_MANY_LEADS -> "Ведут не больше двух — так хватит сил на каждую."
        Violation.LEVER_NOT_ONE -> "Рычаг один."
        Violation.NO_WATER -> E4_WATER_KEEP
        Violation.MARK_WITHOUT_LADDER -> "У этой детали нет шкалы — здесь только практика."
        Violation.MARK_OUT_OF_RANGE -> "Такой рубеж — целиком, не за этот этап."
        Violation.MIRROR_AS_GOAL -> "Это зеркало тела, а не цель."
        Violation.GIVEN_NOT_SAFE -> "С учётом «Дано» — только безопасная версия."
        Violation.DEADLINE_TOO_SOON -> E2_WEEK_SIZED
        Violation.DEADLINE_TOO_FAR -> E2_TOO_FAR
        Violation.CRITERIA_COUNT -> "Что будет правдой к сроку — от одного до трёх пунктов."
        Violation.CRITERION_NOT_RANGE -> "Лучше диапазоном: «от … до …»."
        Violation.WEIGHT_CLOSED -> GATE_WEIGHT_CLOSED
        Violation.WEIGHT_LATER -> GATE_WEIGHT_LATER
    }

    // Diff card (A56 §3).
    const val WAS = "Было"
    const val WILL = "Станет"
    const val APPLY = "Применить"
    const val KEEP = "Не менять"
    const val UNDO = "Вернуть как было"
    const val SOFT_LIMIT = "На этой неделе цель уже пересобрана. Посмотрим в воскресенье?"
    const val SOFT_OVERRIDE = "Всё равно поменять"
    const val TALK = "Срок двигается третий раз. Может, цель другая? Можно обсудить с наставником."
    const val WEEK_NOW = "Цели недели — сейчас"
    const val EFFECT_TODAY_SAME = "Сегодня: дела не меняются, новое — с завтра."
    const val EFFECT_TODAY_SAFETY = "Сегодня: опасные дела — в безопасной версии, сразу."
    const val EFFECT_TODAY_PAUSE = "Сегодня: дела по цели снимаются с завтра, опоры остаются."
    const val EFFECT_WEEK_NEXT = "Неделя: цели недели — со следующей недели."
    const val EFFECT_WEEK_NOW = "Неделя: цели недели пересоберу сейчас."
    const val EFFECT_WEEK_NONE = "Неделя: ничего не меняется."
    const val EFFECT_EXPERIMENT_SAME = "Эксперимент недели остаётся до воскресенья."
    const val UPDATED_TODAY = "обновлено под новый срок"

    const val CHANGE_OVERLINE = "Изменение"
    const val WHAT_CHANGES = "Что поменяется"
    const val LOOK_SUNDAY = "Посмотрим в воскресенье"
    const val DIFF_TITLE = "Название"
    const val DIFF_WHY = "Зачем"
    const val DIFF_BE = "Кем буду"
    const val DIFF_DEADLINE = "Срок"
    const val DIFF_MODE = "Как держим"
    const val DIFF_STATUS = "Состояние"
    const val DIFF_PARTS = "Детали"
    const val DIFF_LEVER = "Рычаг"
    const val DIFF_CRITERIA = "Правда к сроку"
    const val DIFF_START = "Начало этапа"
    const val DIFF_NONE = "—"

    fun statusWord(s: GoalStatus): String = when (s) {
        GoalStatus.DRAFT -> "черновик"
        GoalStatus.ACTIVE -> "в пути"
        GoalStatus.PAUSED -> "пауза"
        GoalStatus.HELD -> "стоит"
        GoalStatus.REVIEW -> "встреча"
        GoalStatus.TAKEN -> "взята"
        GoalStatus.RELEASED -> "отпущена"
        GoalStatus.QUEUED -> "в «Несу»"
    }

    fun criterion(c: Criterion): String = "${c.label}: ${num(c.low)}…${num(c.high)}${if (c.unit.isBlank()) "" else " " + c.unit}"

    fun part(c: GoalComponent): String = PathLexicon.name(c.key) + " · " + PathLexicon.role(c.role) +
        (c.targetMark?.let { " · «${PathLexicon.mark(it)}»" } ?: "")

    fun changeTitle(change: GoalChange): String = when (change) {
        is GoalChange.MoveDeadline -> "Сдвинуть срок на ${date(change.deadline)}"
        is GoalChange.SetMode -> "Держим ${PathLexicon.mode(change.mode)}"
        is GoalChange.Pause -> PAUSE
        GoalChange.Resume -> RESUME
        is GoalChange.Release -> RELEASE_TITLE
        is GoalChange.Crossroads -> crossroads(change.option)
        is GoalChange.SetLever -> lever(PathLexicon.name(change.key))
        else -> "Пересобрать цель"
    }

    private fun num(d: Double): String = if (d == kotlin.math.floor(d)) d.toLong().toString() else d.toString().replace('.', ',')

    // Milestones (A55 §1 block 8).
    const val MEASURE = "Замер · 10 мин"

    fun milestone(week: Int, d: LocalDate): String =
        if (week >= StageRules.WEEKS) "Встреча · ${date(d)}" else "Веха $week · ${date(d)}"

    const val CHOOSE = "Выбрать"
    const val MORE = "Ещё"
    const val TALK_SUNDAY = "Поговорим в воскресенье"
    const val PAUSE_ON = "Поставить на паузу"
    const val RELEASE_ON = "Отпустить цель"
    const val DONE = "Готово"
    const val CRISIS_LINE = "Если сейчас тяжело — помощь рядом, в два касания."

    // Pause (A56 §3). Черновик, ревью 10.
    const val PAUSE_TITLE = "Что происходит?"
    const val PAUSE_TERM = "Надолго?"
    val PAUSE_TERMS = listOf("1 неделя", "2 недели", "пока не вернусь")
    const val PAUSE_NO_TERM = "Без срока. Путь подождёт."
    const val RETURN_TITLE = "С возвращением. Всё взятое при тебе."
    val RETURN_CARD = listOf("Вернуться", "Ещё неделя", "Отпустить")
    val RETURN_PACE = listOf("медленно", "ровно", "как было")
    const val PUSH_RETURN = "Пауза подошла к концу. Вернуться к пути можно в любой день."

    fun pauseReason(r: PauseReason): String = when (r) {
        PauseReason.UNWELL -> "нездоровится"
        PauseReason.KIDS_SICK -> "дети болеют"
        PauseReason.GRIEF -> "горе"
        PauseReason.VACATION -> "отпуск"
        PauseReason.TOO_MUCH -> "много всего"
        PauseReason.PRIVATE -> "не хочу говорить"
    }

    // Crossroads (A56 §3). Черновик, ревью 10, 3.
    const val CROSSROADS = "Перекрёсток"
    const val CROSSROADS_LAG = "Темп ниже расчёта — это данные, не оценка."
    const val CROSSROADS_RETURN = "Догонять ничего не нужно. Как пойдём дальше?"
    const val CROSSROADS_UNDO = "Выбор можно вернуть в течение 7 дней."

    fun crossroads(o: CrossroadsOption): String = when (o) {
        CrossroadsOption.SHIFT_DEADLINE -> "Сдвинуть срок"
        CrossroadsOption.LESS_BY_DATE -> "К сроку — меньше"
        CrossroadsOption.REBUILD_FROM_TODAY -> "Пересобрать от сегодня"
        CrossroadsOption.EASE_LEAD -> "Облегчить ведущую"
        CrossroadsOption.SWITCH_LEVER -> "Сменить рычаг"
        CrossroadsOption.AS_IS -> "Как есть"
    }

    // Release (A56 §3). Черновик, ревью 10.
    const val RELEASE_TITLE = "Отпустить цель"
    const val RELEASE_KEEP = "Всё, что взято на этом пути, останется во «Взято»."
    const val RELEASE_TO_QUEUE = "Положить цель в «Несу»"
    const val RELEASE_UNDO = "Передумать можно в течение суток."

    /** Every text above, for the stop-dictionary tests (P-19, A39). */
    fun all(): List<String> = listOf(
        REVIEW, EMPTY_TITLE, EMPTY_LINE, NAME_GOAL, FROM_CARRYING, OVERLINE, WHY_EMPTY, SLICE_A11Y, GIVEN_EMPTY,
        STAGE_OVERLINE, MILESTONES, BALANCE_EVEN, BALANCE_EVEN_SUB, HEAVY_TITLE, HEAVY_SUB, PAUSED_TITLE, PAUSED_SUB,
        HELD_TITLE, HELD_SUB, STATUS_LEADS, STATUS_PROPS, STATUS_HOLDS, STATUS_KEPT, OUTSIDE, PRACTICE, MARK_UNKNOWN,
        LEVER_BADGE, SAFE_BADGE, MENU, MOVE_DEADLINE, SWITCH_MODE, PAUSE, RESUME, RELEASE, HISTORY, MARK_TAKEN_ACTION,
        MARK_PICK, stage(1, 2), week(5), toMilestone(17), toMilestone(0), lever("Сон"), leverSub("Сон"), leverStatus("Сон"),
        markLine(2, 3), markLine(null, null), more(3, "Небо"), markTaken(3), horizonName(null, LocalDate.of(2026, 12, 31)),
        horizonLine(5), HORIZON_SEATED, HORIZON_MEETING, HORIZON_RULE, FIRE_WITH_YOU, FIRE_ON_HORIZON, FIRE_SHARED, SKIP, HELP,
        BACK, E1_TITLE, E1_SUB, E1_OWN, E1_HINT, E1_CUSTOM, e1Carrying(2), e1FromSleep("6,2"), E2_TITLE, E2_WHY, E2_WHY_HINT,
        E2_DEADLINE, E2_WEEK_SIZED, E2_TOO_FAR, E3_TITLE, E3_SUB, E3_HINT, e3Default(LocalDate.of(2026, 12, 31)), E4_TITLE,
        E4_SUB, E4_ADD, E4_REMOVE, E4_WATER_KEEP, e4Lever("Сон", "6,2 ч из 7"), e4Given("колено"), e4Start("весна 2025"),
        E5_TITLE, E5_FROM_CARRYING, E5_OWN, E5_IF_THEN, e5IfThen("в 21:00 тянет к холодильнику"), E5_FIRST_WEEK, E6_TITLE,
        E6_ON_TRACK, E6_NO_PACE, E6_PROCESS_ONLY, E6_NOT_CEILING, E6_BY_DATE, E6_FULL, E6_BY_DATE_SUB, E6_FULL_SUB, E7_COMING,
        E7_LETTER, E7_LETTER_HINT, e7FirstFire("Сила"), GATE_MEDICAL, GATE_HARMFUL, GATE_WEIGHT_CLOSED, GATE_WEIGHT_LATER,
        WAS, WILL, APPLY, KEEP, UNDO, SOFT_LIMIT, SOFT_OVERRIDE, TALK, WEEK_NOW, EFFECT_TODAY_SAME, EFFECT_TODAY_SAFETY,
        EFFECT_TODAY_PAUSE, EFFECT_WEEK_NEXT, EFFECT_WEEK_NOW, EFFECT_WEEK_NONE, EFFECT_EXPERIMENT_SAME, UPDATED_TODAY,
        PAUSE_TITLE, PAUSE_TERM, PAUSE_NO_TERM, RETURN_TITLE, PUSH_RETURN, CROSSROADS, CROSSROADS_LAG, CROSSROADS_RETURN,
        CROSSROADS_UNDO, RELEASE_TITLE, RELEASE_KEEP, RELEASE_TO_QUEUE, RELEASE_UNDO, A11Y_OPEN, A11Y_OUTSIDE,
        A11Y_PRACTICE, A11Y_PAUSED, A11Y_EXCLUDED, A11Y_WHY_EMPTY, a11yWhy("силы на лето"), a11yGiven(listOf("колено")),
        a11yFireMoved(3, ComponentKey.STRENGTH), CHANGE_OVERLINE, WHAT_CHANGES, LOOK_SUNDAY, DIFF_TITLE, DIFF_WHY, DIFF_BE,
        DIFF_DEADLINE, DIFF_MODE, DIFF_STATUS, DIFF_PARTS, DIFF_LEVER, DIFF_CRITERIA, DIFF_START, MEASURE, CHOOSE, TALK_SUNDAY,
        PAUSE_ON, RELEASE_ON, DONE, MORE, CRISIS_LINE, milestone(4, LocalDate.of(2026, 12, 5)), milestone(12, LocalDate.of(2027, 1, 30)),
        criterion(Criterion("ночи от 7 ч", 4.0, 5.5, "из 7")), part(GoalComponent(ComponentKey.SLEEP, Role.LEAD, 3)),
        changeTitle(GoalChange.MoveDeadline(LocalDate.of(2027, 2, 28))),
        a11yPart(SlicePart(ComponentKey.STRENGTH, PartState.MARKS, Role.LEAD, 2, 3, lever = true, safeVersion = "без прыжков"), "6,1 ч из 7", true),
        a11yPart(SlicePart(ComponentKey.FUEL, PartState.PRACTICE, Role.PROP), null, false),
        a11yPart(SlicePart(ComponentKey.OUTLOOK, PartState.OFF), null, false),
        a11yHorizon(HorizonTitan(1, "Ты к 31.12", listOf(HorizonFire(ComponentKey.SLEEP, HorizonLight.ON_HORIZON), HorizonFire(ComponentKey.FEELINGS, HorizonLight.SHARED)), 5, true), false),
        a11ySlice("Силы на вечер", Balance(BalanceKind.EVEN, BALANCE_EVEN, BALANCE_EVEN_SUB, null)),
    ) + CTA + E2_DEADLINES + PAUSE_TERMS + RETURN_CARD + RETURN_PACE + PauseReason.entries.map(::pauseReason) +
        CrossroadsOption.entries.map(::crossroads) + GoalStatus.entries.map(::statusWord) + Violation.entries.map(::violation) + PathTemplates.ALL.map { it.title } +
        PathTemplates.ALL.map { it.obstacleHint } + PathTemplates.ALL.flatMap { t -> t.criteria.map { it.label } } + PathLexicon.all()
}
