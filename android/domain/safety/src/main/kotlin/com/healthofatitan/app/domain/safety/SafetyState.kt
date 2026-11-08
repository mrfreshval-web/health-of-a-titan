package com.healthofatitan.app.domain.safety

/**
 * An active flag (A60 §3.1: `{code, source, since, expiresAt, exitRule}`); the exit rule is the
 * flag's own — the crisis window or the person's choice for `ED_CARE`.
 */
data class ActiveFlag(val flag: SafetyFlag, val since: Long, val until: Long? = null, val source: String? = null)

/**
 * The one safety state of HoT (A58 §2, A60 §3, D-051). «Сегодня», «Неделя» and «Путь» read it
 * from the bus and ask [restrictions] for their own column of the matrix; none of them keeps a
 * flag of its own.
 *
 * @property unknown the journal holds a safety event this build does not know: rule 3 of the
 *   bus, an unknown flag means «forbid» — every consumer gets its strictest set
 */
data class SafetyState(
    val flags: Map<SafetyFlag, ActiveFlag> = emptyMap(),
    val edCare: EdCareState = EdCareState.NONE,
    val unknown: Boolean = false,
) {
    operator fun contains(flag: SafetyFlag): Boolean = flag in flags

    /** CrisisGuard ≥ 2 within its window (A07 §4.3): the crisis is stronger than any state (L-13). */
    val crisis: Boolean get() = SafetyFlag.EMERGENCY in flags || SafetyFlag.CRISIS_L2 in flags || SafetyFlag.CRISIS_L3 in flags

    /** The column of [consumer]: the union over all active flags — the strictest wins. */
    fun restrictions(consumer: SafetyConsumer): Set<Restriction> {
        if (unknown) return SafetyPolicy.strictest(consumer)
        val fromFlags = flags.keys.flatMapTo(mutableSetOf()) { SafetyPolicy.of(consumer, it) }
        return fromFlags + (edCare.afterExit() intersect SafetyPolicy.of(consumer, SafetyFlag.ED_CARE))
    }

    fun forbids(consumer: SafetyConsumer, restriction: Restriction): Boolean = restriction in restrictions(consumer)

    companion object {
        /** «Норма»: no flag. */
        val NORMAL = SafetyState()
    }
}
