plugins {
    alias(libs.plugins.hot.jvm.library)
}

// Винс v1 (A62, D-056): the conversation of level 1, the ContextPack, the checks of the input and
// the answer. Pure Kotlin: CrisisGuard and the safety bus come in as plain values from the app;
// the network and the journal live in :feature:mentor.

dependencies {
    api(project(":core:model"))
}
