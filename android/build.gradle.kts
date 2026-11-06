// All plugins are listed here so that every module shares one build classpath.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
}

/**
 * Module dependency rules (A14 §7, ADR-08). Static check of `project(":…")` references in
 * module build files, so it runs in seconds without resolving anything:
 *  - feature must not depend on another feature (navigate via routes in :core:model);
 *  - domain is pure Kotlin: only :core:model / :core:common, and must use hot.jvm.library;
 *  - integration must not depend on feature;
 *  - core must not depend on feature, domain or integration;
 *  - nothing depends on :app.
 */
val checkModuleGraph by tasks.registering {
    group = "verification"
    description = "Fails the build if module dependencies break the A14 layering rules."
    val rootDirFile = layout.projectDirectory.asFile
    doLast {
        val layers = setOf("app", "core", "domain", "feature", "integration")
        val ref = Regex("""project\(\s*"(:[A-Za-z0-9_:\-]+)"\s*\)""")
        val violations = mutableListOf<String>()
        rootDirFile.walkTopDown()
            .onEnter { it.name != "build" && it.name != "build-logic" && !it.name.startsWith(".") }
            .filter { it.name == "build.gradle.kts" && it.parentFile != rootDirFile }
            .forEach { file ->
                val module = ":" + file.parentFile.relativeTo(rootDirFile).invariantSeparatorsPath.replace('/', ':')
                val layer = module.split(':')[1]
                if (layer !in layers) return@forEach
                val text = file.readText()
                if (layer == "domain" && !text.contains("hot.jvm.library")) {
                    violations += "$module: domain modules must apply hot.jvm.library (no Android)"
                }
                ref.findAll(text).map { it.groupValues[1] }.forEach { dep ->
                    val depLayer = dep.split(':')[1]
                    val bad = when {
                        dep == ":app" -> "nothing may depend on :app"
                        layer == "feature" && depLayer == "feature" -> "feature must not depend on another feature"
                        layer == "domain" && dep !in setOf(":core:model", ":core:common") -> "domain may depend only on :core:model / :core:common"
                        layer == "integration" && depLayer == "feature" -> "integration must not depend on feature"
                        layer == "core" && depLayer in setOf("feature", "domain", "integration") -> "core must not depend on $depLayer"
                        else -> null
                    }
                    if (bad != null) violations += "$module -> $dep: $bad"
                }
            }
        if (violations.isNotEmpty()) {
            throw GradleException("Module graph violations (A14 §7):\n" + violations.joinToString("\n") { "  - $it" })
        }
        logger.lifecycle("checkModuleGraph: OK")
    }
}
