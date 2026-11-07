package com.healthofatitan.app.domain.path

import java.time.LocalDate

/** A goal like Ирина's of A57 §6, taken on a Saturday, in the shape the ritual stores it. */
object PathFixtures {
    val TODAY: LocalDate = LocalDate.of(2026, 11, 7)
    const val NOW: Long = 1_794_000_000_000L
    const val DAY: Long = 24L * 3600_000L
    val FLAGS_ON = PathFlags(weightAndStrength = true)

    val knee = GivenConstraint("knee", "колено: без прыжков", setOf(ComponentKey.MOBILITY, ComponentKey.CARDIO), "без прыжков")

    fun goal(
        components: List<GoalComponent> = listOf(
            GoalComponent(ComponentKey.CARDIO, Role.LEAD, targetMark = 3, startMark = 2),
            GoalComponent(ComponentKey.ATTENTION, Role.PROP),
            GoalComponent(ComponentKey.REST, Role.PROP),
            GoalComponent(ComponentKey.SLEEP, Role.GUARD, targetMark = 3, startMark = 2, isLever = true),
        ),
        status: GoalStatus = GoalStatus.ACTIVE,
        start: LocalDate = TODAY,
        takenAt: Long = NOW,
    ) = PracticalGoal(
        id = 7,
        title = "Силы на вечер и детей",
        why = "чтобы хватало сил на детей",
        beName = "Тот, у кого есть силы на лето",
        templateId = PathTemplates.ENERGY,
        start = start,
        deadline = start.plusWeeks(24),
        status = status,
        criteria = listOf(Criterion("вечера без работы из 7", 3.0, 4.0, "", PathMetric.REST_EVENINGS)),
        components = components,
        takenAt = takenAt,
    )

    fun ctx(nowMs: Long = NOW, today: LocalDate = TODAY, versions: List<GoalVersion> = emptyList(), given: GivenLayer = GivenLayer.NONE) =
        ChangeContext(nowMs = nowMs, today = today, versions = versions, given = given)

    fun ChangeResult.proposal(): ChangeProposal = (this as ChangeResult.Proposed).proposal

    fun ChangeResult.applied(): ChangeResult.Applied = this as ChangeResult.Applied
}
