package com.healthofatitan.app.domain.path

import java.time.LocalDate

/**
 * Text form of a goal for `path_goal_version.snapshot` and the criteria column: one `key=value`
 * line per field, lists as repeated keys, fields of a list item split by `|`. `%`, `|`, `=` and
 * line breaks are percent-encoded. Stable and readable; no JSON library in the domain.
 */
object GoalCodec {
    fun encode(g: PracticalGoal): String = buildList {
        add("title" to g.title)
        g.why?.let { add("why" to it) }
        g.beName?.let { add("be" to it) }
        add("kind" to g.kind.name)
        g.templateId?.let { add("tpl" to it) }
        add("start" to g.start.toString())
        add("deadline" to g.deadline.toString())
        add("stage" to g.stage.toString())
        add("mode" to g.mode.name)
        add("status" to g.status.name)
        g.criteria.forEach { add("crit" to encodeCriterion(it)) }
        g.obstacle?.let { add("obstacle" to it.text); add("ifthen" to it.ifThen) }
        g.components.forEach { add("comp" to encodeComponent(it)) }
        add("paused" to g.pausedDays.toString())
        g.pauseFrom?.let { add("pause_from" to it.toString()) }
        g.pauseReason?.let { add("pause_reason" to it.id) }
        g.pauseUntil?.let { add("pause_until" to it.toString()) }
        add("taken_at" to g.takenAt.toString())
        add("to_queue" to g.releaseToQueue.toString())
    }.joinToString("\n") { (k, v) -> "$k=${esc(v)}" }

    fun decode(text: String, id: Long, version: Int): PracticalGoal? = runCatching {
        val lines = text.lines().filter { it.contains('=') }.map { it.substringBefore('=') to unesc(it.substringAfter('=')) }
        fun one(k: String) = lines.firstOrNull { it.first == k }?.second
        fun many(k: String) = lines.filter { it.first == k }.map { it.second }
        PracticalGoal(
            id = id,
            title = one("title") ?: return null,
            why = one("why"),
            beName = one("be"),
            kind = GoalKind.valueOf(one("kind") ?: "DO"),
            templateId = one("tpl"),
            start = LocalDate.parse(one("start")),
            deadline = LocalDate.parse(one("deadline")),
            stage = one("stage")?.toInt() ?: 1,
            mode = GoalMode.valueOf(one("mode") ?: "BY_DATE"),
            status = GoalStatus.parse(one("status")) ?: GoalStatus.ACTIVE,
            criteria = many("crit").mapNotNull(::decodeCriterion),
            obstacle = one("obstacle")?.let { Obstacle(it, one("ifthen").orEmpty()) },
            components = many("comp").mapNotNull(::decodeComponent),
            version = version,
            pausedDays = one("paused")?.toInt() ?: 0,
            pauseFrom = one("pause_from")?.let(LocalDate::parse),
            pauseReason = PauseReason.fromId(one("pause_reason")),
            pauseUntil = one("pause_until")?.let(LocalDate::parse),
            takenAt = one("taken_at")?.toLong() ?: 0L,
            releaseToQueue = one("to_queue")?.toBoolean() ?: true,
        )
    }.getOrNull()

    fun encodeCriterion(c: Criterion): String = listOf(c.label, c.low.toString(), c.high.toString(), c.unit, c.metric?.id.orEmpty())
        .joinToString("|") { escField(it) }

    fun decodeCriterion(s: String): Criterion? = runCatching {
        val f = s.split('|').map(::unesc)
        Criterion(f[0], f[1].toDouble(), f[2].toDouble(), f[3], PathMetric.fromId(f.getOrNull(4)))
    }.getOrNull()

    fun encodeCriteria(list: List<Criterion>): String = list.joinToString("\n") { esc(encodeCriterion(it)) }

    fun decodeCriteria(s: String): List<Criterion> = s.lines().filter { it.isNotBlank() }.mapNotNull { decodeCriterion(unesc(it)) }

    private fun encodeComponent(c: GoalComponent): String = listOf(
        c.key.id, c.role.name, c.targetMark?.toString().orEmpty(), c.startMark?.toString().orEmpty(), c.safeVersion.orEmpty(), c.isLever.toString(),
    ).joinToString("|") { escField(it) }

    private fun decodeComponent(s: String): GoalComponent? = runCatching {
        val f = s.split('|').map(::unesc)
        GoalComponent(
            key = ComponentKey.fromId(f[0]) ?: return null,
            role = Role.valueOf(f[1]),
            targetMark = f[2].toIntOrNull(),
            startMark = f[3].toIntOrNull(),
            safeVersion = f[4].ifEmpty { null },
            isLever = f[5].toBoolean(),
        )
    }.getOrNull()

    private fun esc(s: String) = s.replace("%", "%25").replace("\n", "%0A").replace("\r", "%0D").replace("=", "%3D")

    private fun escField(s: String) = esc(s).replace("|", "%7C")

    private fun unesc(s: String) = s.replace("%7C", "|").replace("%3D", "=").replace("%0D", "\r").replace("%0A", "\n").replace("%25", "%")
}
