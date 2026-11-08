plugins {
    alias(libs.plugins.hot.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.healthofatitan.app.feature.mentor"
}

// Винс v1 (A62, D-056): the conversation screen, the client of `/llm/mentor`, the summaries of
// the chronicle on the phone. CrisisGuard and the safety bus come from :integration:whoop.
dependencies {
    implementation(project(":integration:whoop"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":core:security"))
    implementation(project(":domain:mentor"))
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
