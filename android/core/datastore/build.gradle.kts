plugins {
    alias(libs.plugins.hot.android.library)
    alias(libs.plugins.hot.hilt)
}

android {
    namespace = "com.healthofatitan.app.core.datastore"
}

// Device settings (A14 §2 DataStore, §7 `:core:datastore`). Nothing here leaves the phone (D-019).
dependencies {
    implementation(project(":core:model"))
    api(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.kotlinx.coroutines.test)
}
