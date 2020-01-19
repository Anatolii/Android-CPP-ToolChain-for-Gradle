plugins {
    `kotlin-dsl`
    id("java-gradle-plugin")
    id("maven-publish")
    id("dev.anatolii.internal.git")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(gradleApi())
}

version = git.generateCalVer()
group = "dev.anatolii.cpp.android.toolchain"

gradlePlugin {
    plugins {
        create("androidClang") {
            id = project.group.toString()
            implementationClass = "dev.anatolii.gradle.cpp.android.AndroidClangCompilerPlugin"
        }
    }
}