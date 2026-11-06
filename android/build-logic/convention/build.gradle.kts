import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

group = "com.healthofatitan.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.room.gradlePlugin)
    implementation(libs.snakeyaml)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = libs.plugins.hot.android.application.get().pluginId
            implementationClass = "HotAndroidApplicationPlugin"
        }
        register("androidLibrary") {
            id = libs.plugins.hot.android.library.get().pluginId
            implementationClass = "HotAndroidLibraryPlugin"
        }
        register("androidFeature") {
            id = libs.plugins.hot.android.feature.get().pluginId
            implementationClass = "HotAndroidFeaturePlugin"
        }
        register("jvmLibrary") {
            id = libs.plugins.hot.jvm.library.get().pluginId
            implementationClass = "HotJvmLibraryPlugin"
        }
        register("hilt") {
            id = libs.plugins.hot.hilt.get().pluginId
            implementationClass = "HotHiltPlugin"
        }
        register("room") {
            id = libs.plugins.hot.room.get().pluginId
            implementationClass = "HotRoomPlugin"
        }
        register("content") {
            id = libs.plugins.hot.content.get().pluginId
            implementationClass = "HotContentPlugin"
        }
    }
}
