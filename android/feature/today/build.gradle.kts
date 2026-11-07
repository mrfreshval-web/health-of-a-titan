plugins {
    alias(libs.plugins.hot.android.feature)
}

android {
    namespace = "com.healthofatitan.app.feature.today"
}

dependencies {
    implementation(project(":integration:whoop"))
    implementation(project(":integration:healthconnect"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
}
