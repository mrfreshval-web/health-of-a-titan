import com.android.build.api.dsl.ApplicationExtension
import com.healthofatitan.buildlogic.HotSdk
import com.healthofatitan.buildlogic.configureCompose
import com.healthofatitan.buildlogic.configureKotlinAndroid
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** hot.android.application: the :app module (Compose, Hilt). */
class HotAndroidApplicationPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("com.android.application")
        extensions.configure<ApplicationExtension> {
            configureKotlinAndroid(this)
            configureCompose(this)
            defaultConfig.targetSdk = HotSdk.TARGET
            defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            testOptions.unitTests.isIncludeAndroidResources = true
            lint.abortOnError = true
            lint.checkReleaseBuilds = false
        }
        pluginManager.apply("hot.hilt")
    }
}
