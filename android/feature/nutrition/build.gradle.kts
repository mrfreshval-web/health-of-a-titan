plugins {
    alias(libs.plugins.hot.android.feature)
}

android {
    namespace = "com.healthofatitan.app.feature.nutrition"
}

dependencies {
    implementation(project(":domain:nutrition"))
    implementation(project(":core:database"))
    // The day (regime, training, bedtime) comes from the existing day engine (A09).
    implementation(project(":integration:whoop"))
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.snakeyaml)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
