plugins {
    `kotlin-dsl`
    id("java-gradle-plugin")
    id("maven-publish")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(gradleApi())
}

version = GitRepository(rootDir).generateCalVer()
group = "dev.anatolii.cpp.android"

gradlePlugin {
    plugins {
        create("androidClang") {
            id = "dev.anatolii.cpp.android.toolchain"
            implementationClass = "dev.anatolii.gradle.cpp.android.AndroidClangCompilerPlugin"
        }
    }
}