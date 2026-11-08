plugins {
    alias(libs.plugins.hot.jvm.library)
}

// The safety bus `SafetyState` (A58 §2, A60 §3, D-051, D-057): one dictionary of flags, the
// policy of every consumer, the rules of `ED_CARE` and the projector that builds the state from
// the event journal. Pure Kotlin: the journal itself lives in :core:database.

dependencies {
    // The codes of the safety events, shared with the journal in :core:database.
    api(project(":core:model"))
}
