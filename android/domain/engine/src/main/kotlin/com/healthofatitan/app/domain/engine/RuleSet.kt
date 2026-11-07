package com.healthofatitan.app.domain.engine

/**
 * Rule ids and versions this engine implements (A09 §3, the YAML files in `content/rules`; A19 §1.2).
 * A changed condition or text means `version + 1` here and in the YAML (A09 §6); stored days keep
 * the `ruleId@ver` they were built with and are never recomputed.
 */
object RuleSet {
    /**
     * `DayState.ruleSet@ver`: manifest of `content/rules` (regime.yaml `ruleSet: day`, version 2).
     * day@2 (A39 expert review): SAF-04 and EVE-01 added, REG-05@2 (no WHOOP Recovery %, manual
     * fallback), SAF-03@2 (see a doctor on the third day of a cold), RHRZ@1 and RRZ@1 read.
     */
    const val VERSION = "day@2"

    /**
     * A25 metric versions the thresholds were tuned on (regime.yaml `metrics_required`, the subset
     * slice 2 reads). A new metric version needs a review of the thresholds (A09 §6 p. 3).
     */
    val METRICS_REQUIRED = listOf("ORS@1", "SLP@1", "SLPDEBT@1", "TRL@1", "ECAP@1", "LRB@1", "RHRZ@1", "RRZ@1")

    // Safety, regime, context (A09 §3)
    val SAF_03 = RuleRef("SAF-03", 2)
    val SAF_04 = RuleRef("SAF-04", 1)
    val REG_01 = RuleRef("REG-01", 1)
    val REG_02 = RuleRef("REG-02", 1)
    val REG_03 = RuleRef("REG-03", 1)
    val REG_04 = RuleRef("REG-04", 1)
    val REG_05 = RuleRef("REG-05", 2)
    val REG_06 = RuleRef("REG-06", 1)
    val CTX_01 = RuleRef("CTX-01", 1)
    val CTX_03 = RuleRef("CTX-03", 1)
    val CTX_04 = RuleRef("CTX-04", 1)

    // Day rules (A09 §3, day_rules.yaml)
    val SLP_01 = RuleRef("SLP-01", 1)
    val SLP_02 = RuleRef("SLP-02", 1)
    val SLP_03 = RuleRef("SLP-03", 1)
    val SLP_05 = RuleRef("SLP-05", 1)
    val NUT_01 = RuleRef("NUT-01", 1)
    val ACT_01 = RuleRef("ACT-01", 1)
    val ACT_03 = RuleRef("ACT-03", 1)
    val WRK_01 = RuleRef("WRK-01", 1)
    val WRK_02 = RuleRef("WRK-02", 1)

    // Training (A19 §1.2): weekly queue (step 2), modifiers TR-05 / TR-07
    val A19_QUEUE = RuleRef("A19-Q", 1)
    val TR_05 = RuleRef("TR-05", 1)
    val TR_07 = RuleRef("TR-07", 1)

    /** Evening deck → next morning (D-027): «тело забито» lightens the training (context.yaml, A39). */
    val EVE_01 = RuleRef("EVE-01", 1)
}
