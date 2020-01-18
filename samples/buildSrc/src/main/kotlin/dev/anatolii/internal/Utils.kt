package dev.anatolii.internal

import GenerateDummyCppSource
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider

fun createDummyCppSourceGenerationTask(project: Project): TaskProvider<GenerateDummyCppSource> {
    return project.tasks.register("generateCppHeaderSourceFile", GenerateDummyCppSource::class.java) {
        val sourceFile = project.layout.buildDirectory.file("dummy-source.cpp")
        outputFile.set(sourceFile)
        symbolName.set("__" + dev.anatolii.internal.toSymbol(project.path) + "_" + dev.anatolii.internal.toSymbol(project.name) + "__")
    }
}

fun toSymbol(s: String): String {
    return s.replace(":", "_").replace("-", "_")
}