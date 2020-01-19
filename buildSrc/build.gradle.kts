repositories {
    mavenCentral()
}

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

dependencies {
    implementation("org.eclipse.jgit:org.eclipse.jgit:5.6.0.201912101111-r")
}

gradlePlugin {
    plugins {
        create("git") {
            id = "dev.anatolii.internal.git"
            implementationClass = "dev.anatolii.internal.GitPlugin"
        }
    }
}