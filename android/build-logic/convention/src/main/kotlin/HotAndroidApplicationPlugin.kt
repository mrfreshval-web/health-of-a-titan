import com.android.build.api.dsl.ApplicationExtension
import com.healthofatitan.buildlogic.HotSdk
import com.healthofatitan.buildlogic.configureCompose
import com.healthofatitan.buildlogic.configureKotlinAndroid
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.kotlin.dsl.configure

/** hot.android.application: the :app module (Compose, Hilt). */
class HotAndroidApplicationPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("com.android.application")
        extensions.configure<ApplicationExtension> {
            configureKotlinAndroid(this)
            configureCompose(this)
            defaultConfig.targetSdk = HotSdk.TARGET
            // AppAuth's redirect activity (WHOOP OAuth, A14 ADR-03) needs the custom scheme in every
            // merged manifest, also in library unit tests: com.healthofatitan.app://oauth/whoop.
            defaultConfig.manifestPlaceholders["appAuthRedirectScheme"] = HotSdk.OAUTH_REDIRECT_SCHEME
            defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            testOptions.unitTests.isIncludeAndroidResources = true
            lint.abortOnError = true
            lint.checkReleaseBuilds = false
        }
        // Failed tests are printed in the CI log, as in hot.android.library.
        tasks.withType(Test::class.java).configureEach {
            testLogging {
                events(TestLogEvent.FAILED)
                exceptionFormat = TestExceptionFormat.FULL
            }
        }
        pluginManager.apply("hot.hilt")
    }
}
