plugins {
    alias(libs.plugins.hot.jvm.library)
    jacoco
}

dependencies {
    // AddressForm (A61 §4.1, D-055): the person's address form is an input of the day plan.
    api(project(":core:model"))
}

// Crisis protocol (A07 §4.8, D-029): CrisisGuard is covered 100 % by the T1–T15 tests.
// CI: ./gradlew :domain:engine:crisisGuardCoverage
val crisisClasses = listOf(
    "**/CrisisGuard*.class",
    "**/CrisisLevel*.class",
    "**/CrisisReason*.class",
    "**/CrisisSource*.class",
    "**/CrisisAssessment*.class",
    "**/SafetyAnswer*.class",
)

tasks.register<JacocoCoverageVerification>("crisisGuardCoverage") {
    group = "verification"
    description = "Fails unless the CrisisGuard tests cover every line and branch of the on-device crisis filter."
    dependsOn(tasks.named("test"))
    executionData(tasks.named<Test>("test").get())
    sourceSets(sourceSets["main"])
    classDirectories.setFrom(files(sourceSets["main"].output.classesDirs).asFileTree.matching { include(crisisClasses) })
    violationRules {
        rule {
            limit {
                counter = "LINE"
                minimum = "1.0".toBigDecimal()
            }
        }
        rule {
            element = "CLASS"
            includes = listOf("com.healthofatitan.app.domain.engine.CrisisGuard")
            limit {
                counter = "BRANCH"
                minimum = "1.0".toBigDecimal()
            }
        }
    }
}
