plugins {
    alias(libs.plugins.hot.jvm.library)
}

dependencies {
    // JSON of the content pipeline (content/training/*.yaml → assets/content/training/*.json).
    implementation(libs.kotlinx.serialization.json)
    api(libs.kotlinx.coroutines.core)

    // Tests read the YAML sources in content/training directly, so they check exactly what the
    // experts wrote (the same tree the JSON is compiled from).
    testImplementation(libs.snakeyaml)
}

val contentDir = rootProject.layout.projectDirectory.dir("../content")

tasks.withType<Test>().configureEach {
    systemProperty("hot.content.dir", contentDir.asFile.absolutePath)
    inputs.dir(contentDir.dir("training")).withPropertyName("trainingContent")
    inputs.dir(contentDir.dir("profile")).withPropertyName("profileContent")
}
