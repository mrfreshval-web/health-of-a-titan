plugins {
    alias(libs.plugins.hot.android.library)
    alias(libs.plugins.hot.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.healthofatitan.app.integration.whoop"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:security"))
    api(project(":core:network"))
    api(project(":core:database"))
    api(project(":domain:metrics"))

    api(libs.appauth)
    api(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
