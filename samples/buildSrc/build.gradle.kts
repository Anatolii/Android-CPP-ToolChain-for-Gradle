repositories {
    mavenCentral()
}

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

dependencies {
    implementation("de.undercouch:gradle-download-task:4.0.2")
}

gradlePlugin {
    plugins {
        create("boost") {
            id = "dev.anatolii.internal.boost"
            implementationClass = "dev.anatolii.internal.boost.BoostPlugin"
        }
    }
}
