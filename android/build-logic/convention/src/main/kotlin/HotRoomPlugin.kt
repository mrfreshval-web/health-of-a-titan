import androidx.room.gradle.RoomExtension
import com.google.devtools.ksp.gradle.KspExtension
import com.healthofatitan.buildlogic.lib
import com.healthofatitan.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType

/**
 * hot.room: Room via KSP with exported schemas in `<module>/schemas` (A14 §9 step 4).
 * Unit tests get the schema dir as a system property so that NoRawWhoopColumnsTest can
 * inspect the exported schema.
 */
class HotRoomPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("androidx.room")
        pluginManager.apply("com.google.devtools.ksp")
        val schemaDir = layout.projectDirectory.dir("schemas")
        extensions.configure<KspExtension> {
            arg("room.generateKotlin", "true")
        }
        extensions.configure<RoomExtension> {
            schemaDirectory(schemaDir.asFile.path)
        }
        dependencies.apply {
            add("implementation", libs.lib("room-runtime"))
            add("implementation", libs.lib("room-ktx"))
            add("ksp", libs.lib("room-compiler"))
        }
        tasks.withType<Test>().configureEach {
            systemProperty("hot.room.schemaDir", schemaDir.asFile.absolutePath)
            systemProperty("hot.room.buildDir", layout.buildDirectory.get().asFile.absolutePath)
            // Schema JSON is produced by KSP and copied by the Room plugin; make sure it exists.
            dependsOn(tasks.matching { it.name.startsWith("copyRoomSchemas") })
            inputs.dir(schemaDir).optional().withPropertyName("roomSchemas")
        }
    }
}
