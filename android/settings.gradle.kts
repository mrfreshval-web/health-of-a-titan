pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "matuta"

// Modules appear in the slice that needs them (A14 §7). Slice 0:
include(":app")
include(":core:model")
include(":core:ui")
include(":core:security")
include(":core:database")
include(":feature:today")
include(":feature:mentor")
include(":feature:week")
include(":feature:me")
include(":feature:sos")
// Slice 1 (WHOOP, personal norm):
include(":core:network")
include(":domain:metrics")
include(":integration:whoop")
// Slice 2 (day rules A09, plan, morning push):
include(":domain:engine")
// Stream A (D-024): phone steps from Health Connect
include(":integration:healthconnect")
// Stream Г «Наставник и Путь» (D-030, D-033, A42): mentor character, 12-week goal, tab «Путь».
include(":core:datastore")
include(":domain:path")
include(":feature:path")
// Stream C «Еда» (A41): nutrition engine and screens.
include(":domain:nutrition")
include(":feature:nutrition")
// Stream B «Тренировка» (A40 §12): configurator, player, TitanPose.
include(":domain:training")
include(":feature:training")
