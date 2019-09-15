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
        create("androidCpp") {
            id = "dev.anatolii.cpp.android.library"
            group = id
            implementationClass = "dev.anatolii.gradle.cpp.android.AndroidCppLibraryPlugin"
        }
        create("crossPlatformLibrary") {
            id = "dev.anatolii.cpp.crossplatform.library"
            group = id
            implementationClass = "dev.anatolii.gradle.cpp.crossplatform.CrossPlatformCppLibraryPlugin"
        }
        create("crossPlatformApplication") {
            id = "dev.anatolii.cpp.crossplatform.application"
            group = id
            implementationClass = "dev.anatolii.gradle.cpp.crossplatform.CrossPlatformCppApplicationPlugin"
        }
    }
}