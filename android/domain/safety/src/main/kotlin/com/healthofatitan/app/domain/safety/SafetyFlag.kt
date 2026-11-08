package com.healthofatitan.app.domain.safety

/** The groups of the dictionary (A60 §3.1). */
enum class FlagGroup { STOP, CLINICAL, GENTLE, DATA }

/**
 * One dictionary of safety flags (A60 §3.1, A58 §2). Every module reads the flags from the bus and
 * never keeps its own copy, so a flag is not lost at the seam of two modules.
 *
 * `AGE_U18` is not here on purpose: by the review of Agent 3 (A60 §3.5) it is not a mode but the
 * block of entry — HoT is 18+ (R-13), the gate stands before the app (`ConsentRecord age18`).
 * `CHRONIC_*` and `INJURY_*` of A60 are one flag each here; the kind lives in the profile.
 *
 * Only [CRISIS_L2], [CRISIS_L3] and [ED_CARE] have producers in this slice (CrisisGuard and the
 * profile of specifics); the others are in the dictionary so that every consumer already has a
 * policy for them ([SafetyPolicy], rule L-05).
 */
enum class SafetyFlag(val group: FlagGroup) {
    EMERGENCY(FlagGroup.STOP),
    CRISIS_L2(FlagGroup.STOP),
    CRISIS_L3(FlagGroup.STOP),

    SICK(FlagGroup.CLINICAL),
    PREGNANT(FlagGroup.CLINICAL),
    POSTPARTUM_12M(FlagGroup.CLINICAL),
    LACTATING(FlagGroup.CLINICAL),
    MENOPAUSE(FlagGroup.CLINICAL),
    ED_CARE(FlagGroup.CLINICAL),
    MEDS_HR(FlagGroup.CLINICAL),
    MEDS_PSY(FlagGroup.CLINICAL),
    CHRONIC(FlagGroup.CLINICAL),
    INJURY(FlagGroup.CLINICAL),
    SCREEN_NOT_CLEARED(FlagGroup.CLINICAL),
    AGE_65P(FlagGroup.CLINICAL),

    /** One name instead of CARE, `crisis_level`, StormNet and `sc6-crisis-net` (A60 §3.1). */
    STORM(FlagGroup.GENTLE),
    GRIEF(FlagGroup.GENTLE),
    RETURN(FlagGroup.GENTLE),
    LIGHT_WEEK(FlagGroup.GENTLE),
    HEALTH_ANXIETY(FlagGroup.GENTLE),

    CALIBRATION(FlagGroup.DATA),
    MANUAL(FlagGroup.DATA),
    ATYPICAL_PHYSIO(FlagGroup.DATA),
    CYCLE(FlagGroup.DATA),
    ;

    companion object {
        fun parse(code: String?): SafetyFlag? = entries.firstOrNull { it.name == code }
    }
}
