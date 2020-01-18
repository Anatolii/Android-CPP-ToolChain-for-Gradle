repositories {
    mavenCentral()
}

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

gradlePlugin {
    plugins {
        create("boost") {
            id = "dev.anatolii.internal.boost"
            implementationClass = "dev.anatolii.internal.boost.BoostPlugin"
        }
    }
}
