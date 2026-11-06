import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.healthofatitan.buildlogic.ContentCompileTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.register

/**
 * hot.content: registers `contentCompile` (repo-root `content/` → JSON assets) and adds its
 * output to every variant's assets. Apply after hot.android.application.
 */
class HotContentPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        val contentCompile = tasks.register<ContentCompileTask>("contentCompile") {
            group = "build"
            description = "Validates content/*.yaml and compiles it to JSON assets (A14 §2)."
            // The Gradle root is android/, content lives at the repo root: android/../content.
            contentDir.set(rootProject.layout.projectDirectory.dir("../content"))
            outputDir.set(layout.buildDirectory.dir("generated/hot/content"))
        }
        extensions.configure<ApplicationAndroidComponentsExtension> {
            onVariants { variant ->
                variant.sources.assets?.addGeneratedSourceDirectory(contentCompile, ContentCompileTask::outputDir)
            }
        }
    }
}
