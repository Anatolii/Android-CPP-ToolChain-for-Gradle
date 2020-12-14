import dev.anatolii.gradle.cpp.android.CppLibraryAndroid
import org.gradle.nativeplatform.Linkage.SHARED
import org.gradle.nativeplatform.Linkage.STATIC

plugins {
    id("dev.anatolii.cpp.android.toolchain") apply false
}

subprojects {
    apply(plugin = "org.gradle.maven-publish")
    pluginManager.withPlugin("dev.anatolii.cpp.android.toolchain") {
        extensions.getByType(CppLibraryAndroid::class.java).apply {
            apis = listOf(29)
        }
    }
    pluginManager.withPlugin("cpp-library") {
        extensions.getByType(CppLibrary::class.java).apply {
            linkage.addAll(SHARED, STATIC)
        }
        tasks.withType(AbstractNativeCompileTask::class.java).configureEach {
            compilerArgs.add("-std=c++14")
            compilerArgs.add("-v")
        }
    }

    val assembleTaskName = "assemble"
    tasks.all {
        if (name.startsWith(assembleTaskName) && name != assembleTaskName) {
            tasks.findByName(assembleTaskName)?.dependsOn(this)
        }
    }
}

tasks.register("assembleAllCompatibleModules") {
    dependsOn(":boost:${name}")
    dependsOn(":icu:assemble")
}
tasks.register("assembleAllQuarantinedModules") {
    dependsOn(":boost:${name}")
}