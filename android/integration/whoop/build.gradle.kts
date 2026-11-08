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
    // The HoT account (A65, D-062): WHOOP tokens are asked for with the account session.
    api(project(":core:account"))
    api(project(":core:network"))
    api(project(":core:database"))
    api(project(":domain:metrics"))
    api(project(":domain:engine"))
    implementation(project(":domain:checkin"))
    api(project(":domain:today"))
    // The safety bus (A60 §3, D-051): the journal → SafetyState for «Сегодня», «Неделя» and «Путь».
    api(project(":domain:safety"))

    api(libs.appauth)
    api(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
}
