plugins {
    alias(libs.plugins.hot.android.feature)
}

android {
    namespace = "com.healthofatitan.app.feature.checkin"
}

// State cards (A44 v0.2, D-036): the wake deck «вслепую», the reveal «ты ↔ WHOOP», «После ритуала»,
// the evening additions to «Закрыть день», the storm net, discoveries and the collection, the S5
// layer «Только ты», «Что спрашивать» and local usage metrics.
dependencies {
    implementation(project(":domain:checkin"))
    implementation(project(":domain:engine"))
    implementation(project(":core:datastore"))
    implementation(project(":core:security"))
    implementation(project(":integration:whoop"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
