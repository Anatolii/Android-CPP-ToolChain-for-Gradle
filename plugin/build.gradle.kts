plugins {
    `kotlin-dsl`
    id("java-gradle-plugin")
    id("maven-publish")
}

repositories {
    jcenter()
}

dependencies {
    implementation(gradleApi())
}

version = GitRepository(rootDir).generateCalVer()

gradlePlugin {
    plugins {
        create("androidClang") {
            id = "dev.anatolii.cpp.android.clang"
            group = id
            implementationClass = "dev.anatolii.gradle.cpp.android.AndroidClangCompilerPlugin"
        }
    }
}