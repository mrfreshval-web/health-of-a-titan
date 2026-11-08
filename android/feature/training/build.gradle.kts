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

    testImplementation(libs.robolectric)
    testImplementation(libs.snakeyaml)
    testImplementation(libs.androidx.test.ext.junit)
}
