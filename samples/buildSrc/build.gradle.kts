repositories {
    mavenCentral()
}

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

dependencies {
    implementation("de.undercouch:gradle-download-task:4.0.4")
}

gradlePlugin {
    plugins {
        val boost by creating {
            id = "dev.anatolii.internal.boost"
            implementationClass = "dev.anatolii.internal.plugin.boost.BoostPlugin"
        }
        val upstream by creating {
            id = "dev.anatolii.internal.upstream"
            implementationClass = "dev.anatolii.internal.plugin.upstream.UpstreamSources"
        }
    }
}
