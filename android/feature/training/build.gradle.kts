plugins {
    alias(libs.plugins.hot.android.feature)
}

android {
    namespace = "com.healthofatitan.app.feature.training"
}

dependencies {
    api(project(":domain:training"))
    implementation(project(":integration:whoop"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    // Lottie of the top-20 exercises and the heroic moments (Agent 17, tools/lottie → assets/lottie).
    implementation(libs.lottie.compose)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
}
