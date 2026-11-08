plugins {
    alias(libs.plugins.hot.android.library)
    alias(libs.plugins.hot.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.healthofatitan.app.core.account"
}

// The HoT account on the phone (A65 §2–3, D-062): the device key in the Android Keystore, the
// refresh token sealed with Tink, the access token in memory, Google sign-in via Credential Manager.
dependencies {
    implementation(project(":core:security"))
    api(project(":core:network"))

    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
