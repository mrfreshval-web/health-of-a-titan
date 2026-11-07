plugins {
    alias(libs.plugins.hot.android.library)
    alias(libs.plugins.hot.hilt)
}

android {
    namespace = "com.healthofatitan.app.integration.healthconnect"
}

// Health Connect: only the phone's own steps (D-024, A35 §2, A18 И3b.5). Nothing is stored.
dependencies {
    api(project(":domain:engine"))
    api(libs.androidx.health.connect)
    api(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.kotlinx.coroutines.test)
}
