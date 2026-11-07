// «Неделя» (A52 v0.1, D-044, D-045): seven blocks, «Дни в опоре», the goals of the week, the
// Sunday ritual «Итог и сборка» and the Titan of the week. Rules live in :domain:today (week).
plugins {
    alias(libs.plugins.hot.android.feature)
}

android {
    namespace = "com.healthofatitan.app.feature.week"
}

dependencies {
    implementation(project(":integration:whoop"))
    implementation(project(":domain:today"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
