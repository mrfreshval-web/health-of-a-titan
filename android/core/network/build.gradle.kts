plugins {
    alias(libs.plugins.hot.android.library)
    alias(libs.plugins.hot.hilt)
}

android {
    namespace = "com.healthofatitan.app.core.network"
}

dependencies {
    api(libs.okhttp)
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.coroutines.android)
}
