import com.healthofatitan.buildlogic.configureKotlinJvm
import com.healthofatitan.buildlogic.lib
import com.healthofatitan.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project

/** hot.jvm.library: pure Kotlin modules without Android (core:model, domain:*). */
class HotJvmLibraryPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        configureKotlinJvm()
        dependencies.apply {
            add("testImplementation", libs.lib("junit4"))
            add("testImplementation", libs.lib("kotlin-test"))
        }
    }
}
