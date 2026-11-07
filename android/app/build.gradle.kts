import java.util.Properties

plugins {
    alias(libs.plugins.hot.android.application)
    alias(libs.plugins.hot.content)
}

// Build config (A14 §9 step 9): env var wins, then android/local.properties (git-ignored).
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.isFile) file.inputStream().use(::load)
}

fun config(name: String): String? =
    providers.environmentVariable(name).orNull?.takeIf { it.isNotBlank() }
        ?: localProperties.getProperty(name)?.takeIf { it.isNotBlank() }

// Monotonic versionCode from the CI run number, so every APK installs over the previous one.
val ciRunNumber = providers.environmentVariable("GITHUB_RUN_NUMBER").orNull?.toIntOrNull() ?: 1

android {
    namespace = "com.healthofatitan.app"

    defaultConfig {
        applicationId = "com.healthofatitan.app"
        versionCode = ciRunNumber
        versionName = "0.0.$ciRunNumber"
        // Not a secret: the Worker address (repository variable EDGE_BASE_URL). The WHOOP client id
        // comes from the Worker (/whoop/authorize), the client secret never leaves it (ADR-03).
        buildConfigField("String", "EDGE_BASE_URL", "\"${config("EDGE_BASE_URL").orEmpty()}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    // Release signing only when the keystore is provided (GitHub Secrets HOT_KEYSTORE_*).
    // Without it, `assembleRelease` yields an unsigned APK and CI skips the release step.
    val keystoreFile = config("HOT_KEYSTORE_FILE")?.let(::file)?.takeIf { it.isFile }
    if (keystoreFile != null) {
        signingConfigs {
            create("release") {
                storeFile = keystoreFile
                storePassword = config("HOT_KEYSTORE_PASSWORD")
                keyAlias = config("HOT_KEY_ALIAS")
                keyPassword = config("HOT_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false // R8 rules for SQLCipher/Tink come with a later slice.
            signingConfig = signingConfigs.findByName("release")
        }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:ui"))
    implementation(project(":core:security"))
    implementation(project(":core:database"))
    implementation(project(":core:network"))
    implementation(project(":integration:whoop"))
    implementation(project(":integration:healthconnect"))
    implementation(project(":feature:today"))
    implementation(project(":feature:mentor"))
    implementation(project(":feature:week"))
    implementation(project(":feature:me"))
    implementation(project(":feature:sos"))
    implementation(project(":feature:path"))
    implementation(project(":core:datastore"))
    implementation(project(":domain:path"))
    implementation(project(":feature:nutrition"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(project(":domain:nutrition"))
    testImplementation(libs.snakeyaml)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
