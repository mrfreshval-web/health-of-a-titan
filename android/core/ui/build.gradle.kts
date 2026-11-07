plugins {
    alias(libs.plugins.hot.android.library)
    alias(libs.plugins.compose)
}

android {
    namespace = "com.healthofatitan.app.core.ui"
    buildFeatures.compose = true
}

dependencies {
    api(project(":core:model"))
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
