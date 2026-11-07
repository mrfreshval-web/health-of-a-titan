plugins {
    alias(libs.plugins.hot.android.library)
    alias(libs.plugins.hot.hilt)
    alias(libs.plugins.hot.room)
}

android {
    namespace = "com.healthofatitan.app.core.database"
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:security"))
    implementation(libs.sqlcipher.android)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
