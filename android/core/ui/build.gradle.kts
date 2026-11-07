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
    // Lottie of the Titan (Agent 17, tools/lottie → assets/lottie): the player and «Сегодня».
    api(libs.lottie.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
