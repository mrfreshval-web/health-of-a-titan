plugins {
    alias(libs.plugins.hot.jvm.library)
}

dependencies {
    api(project(":core:model"))
    // Tests read the real content/*.yaml (A41 tests: _tests/profiles.yaml, A09 scenarios).
    testImplementation(libs.snakeyaml)
}
