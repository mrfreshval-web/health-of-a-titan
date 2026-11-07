plugins {
    alias(libs.plugins.hot.android.feature)
}

android {
    namespace = "com.healthofatitan.app.feature.path"
}

// Stream Г «Наставник и Путь» (D-030, D-033, A42): tab «Путь», the 12-week goal, the step of the
// day for «Сегодня» and the mentor's character.
dependencies {
    implementation(project(":domain:path"))
    implementation(project(":core:datastore"))
    implementation(project(":integration:whoop"))
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
