plugins {
    alias(libs.plugins.hot.android.library)
    alias(libs.plugins.hot.hilt)
    alias(libs.plugins.hot.room)
}

android {
    namespace = "com.healthofatitan.app.core.database"
}

dependencies {
    implementation(project(":core:security"))
    implementation(libs.sqlcipher.android)
    implementation(libs.kotlinx.coroutines.android)
}
