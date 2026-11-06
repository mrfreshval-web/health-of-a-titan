import com.android.build.api.dsl.LibraryExtension
import com.healthofatitan.buildlogic.HotSdk
import com.healthofatitan.buildlogic.configureKotlinAndroid
import com.healthofatitan.buildlogic.lib
import com.healthofatitan.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.kotlin.dsl.configure

/** hot.android.library: core:* Android modules (no Compose unless they opt in). */
class HotAndroidLibraryPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("com.android.library")
        extensions.configure<LibraryExtension> {
            configureKotlinAndroid(this)
            testOptions.targetSdk = HotSdk.TARGET
            // AppAuth's redirect activity (WHOOP OAuth, A14 ADR-03) needs the custom scheme in every
            // merged manifest, also in library unit tests: com.healthofatitan.app://oauth/whoop.
            defaultConfig.manifestPlaceholders["appAuthRedirectScheme"] = HotSdk.OAUTH_REDIRECT_SCHEME
            lint.targetSdk = HotSdk.TARGET
            defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            testOptions.unitTests.isIncludeAndroidResources = true
        }
        // Modules without tests yet (e.g. :feature:me) still get Hilt-generated test sources;
        // Gradle 9 would fail such a test task with "no discovered tests".
        // Failed tests are printed in the CI log (the HTML report is an artifact that is slow to reach).
        tasks.withType(Test::class.java).configureEach {
            failOnNoDiscoveredTests.set(false)
            testLogging {
                events(TestLogEvent.FAILED)
                exceptionFormat = TestExceptionFormat.FULL
            }
        }
        dependencies.apply {
            add("testImplementation", libs.lib("junit4"))
            add("testImplementation", libs.lib("kotlin-test"))
        }
    }
}
