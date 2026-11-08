plugins {
    alias(libs.plugins.hot.android.feature)
}

android {
    namespace = "com.healthofatitan.app.feature.path"
}

// «Путь» v1 (D-046…D-049, A54–A58): tab «Путь» with «Срез мира», the ritual «Назови цель», the
// Titan of the horizon; and the mentor's character under the avatar.
dependencies {
    implementation(project(":domain:path"))
    // The safety bus (A60 §3): PathSafety reads SafetyState instead of the goal alone.
    implementation(project(":domain:safety"))
    // The rig of the Titan: the horizon Titan is drawn in code on the day Titan's joints (A58-VP §2).
    implementation(project(":domain:training"))
    // CrisisGuard over every free text of the ritual (A07).
    implementation(project(":domain:engine"))
    implementation(project(":domain:metrics"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":integration:whoop"))
    implementation(libs.kotlinx.coroutines.android)

    // The stop dictionary of A39 for the texts of the path.
    testImplementation(project(":domain:today"))
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
