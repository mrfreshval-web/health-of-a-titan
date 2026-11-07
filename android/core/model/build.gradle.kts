plugins {
    alias(libs.plugins.hot.jvm.library)
}

dependencies {
    // Tests read the real content/*.yaml into the same tree the app reads from JSON assets.
    testImplementation(libs.snakeyaml)
}
