plugins {
    alias(libs.plugins.hot.android.library)
    alias(libs.plugins.hot.hilt)
}

android {
    namespace = "com.healthofatitan.app.core.security"
}

dependencies {
    api(libs.tink.android)
}
