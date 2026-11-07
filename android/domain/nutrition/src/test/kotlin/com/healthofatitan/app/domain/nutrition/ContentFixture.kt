package com.healthofatitan.app.domain.nutrition

import com.healthofatitan.app.core.model.content.Tree
import com.healthofatitan.app.core.model.profile.AllergySeverity
import com.healthofatitan.app.core.model.profile.UserConstraints
import com.healthofatitan.app.core.model.content.obj
import com.healthofatitan.app.core.model.content.str
import com.healthofatitan.app.core.model.content.strings
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import java.io.File

/** The real content of the repository, loaded the way the app reads its JSON assets. */
object ContentFixture {

    private val root: File by lazy {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "content/nutrition").isDirectory) dir = dir.parentFile
        requireNotNull(dir) { "content/nutrition not found above ${File("").absolutePath}" }
    }

    fun yaml(path: String): Tree = Yaml(SafeConstructor(LoaderOptions())).load<Any?>(File(root, "content/$path").readText())

    val content: NutritionContent by lazy {
        NutritionContent.parse(
            mapOf(
                "constraints" to yaml("profile/constraints.yaml"),
                "components" to yaml("nutrition/components.yaml"),
                "meals" to yaml("nutrition/meals.yaml"),
                "templates" to yaml("nutrition/templates.yaml"),
                "methods" to yaml("nutrition/methods.yaml"),
                "rules" to yaml("nutrition/rules.yaml"),
            ),
        )
    }

    /** A test profile of `content/nutrition/_tests/profiles.yaml`. */
    data class TestProfile(val id: String, val level: Level, val constraints: UserConstraints, val expect: Map<String, Tree>)

    val testProfiles: List<TestProfile> by lazy {
        (yaml("nutrition/_tests/profiles.yaml").obj()["profiles"] as List<*>).map { it.obj() }.map { p ->
            TestProfile(
                id = p.str("id").orEmpty(),
                level = Level.of(p.str("level")) ?: Level.BASE,
                constraints = UserConstraints(
                    allergies = (p["allergies"] as? List<*>).orEmpty().map { it.obj() }.associate {
                        it.str("id").orEmpty() to (AllergySeverity.of(it.str("severity") ?: "allergy") ?: AllergySeverity.ALLERGY)
                    },
                    intolerances = p.strings("intolerances").toSet(),
                    diets = p.strings("diets").toSet(),
                    medical = p.strings("medical").toSet(),
                    lifeStage = p.strings("life_stage").toSet(),
                ),
                expect = p.obj("expect"),
            )
        }
    }

    fun profile(id: String): TestProfile = testProfiles.first { it.id == id }
}
