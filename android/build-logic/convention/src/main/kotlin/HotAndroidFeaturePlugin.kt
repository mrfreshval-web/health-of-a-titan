import com.android.build.api.dsl.LibraryExtension
import com.healthofatitan.buildlogic.configureCompose
import com.healthofatitan.buildlogic.lib
import com.healthofatitan.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * hot.android.feature: Compose + Hilt + Navigation. A feature never depends on another
 * feature; it gets routes from :core:model and UI kit from :core:ui (checkModuleGraph).
 */
class HotAndroidFeaturePlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("hot.android.library")
        pluginManager.apply("hot.hilt")
        extensions.configure<LibraryExtension> {
            configureCompose(this)
        }
        dependencies.apply {
            add("implementation", project(":core:model"))
            add("implementation", project(":core:ui"))
            add("implementation", libs.lib("androidx-navigation-compose"))
            add("implementation", libs.lib("androidx-hilt-navigation-compose"))
            add("implementation", libs.lib("androidx-lifecycle-runtime-compose"))
            add("implementation", libs.lib("androidx-lifecycle-viewmodel-compose"))
        }
    }
}
