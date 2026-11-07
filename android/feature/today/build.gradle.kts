plugins {
    alias(libs.plugins.hot.android.feature)
}

android {
    namespace = "com.healthofatitan.app.feature.today"
}

dependencies {
    implementation(project(":integration:whoop"))
    implementation(project(":integration:healthconnect"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)

    // UX review 119: the plan's numbers and gestures are checked in Robolectric (R1, R3).
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
