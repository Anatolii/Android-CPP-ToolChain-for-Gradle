package dev.anatolii.internal

import GenerateDummyCppSource
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider

class DummyCpp {
    companion object {
        fun registerSourceGenerationTask(project: Project): TaskProvider<GenerateDummyCppSource> {
            return project.tasks.register("generateCppHeaderSourceFile", GenerateDummyCppSource::class.java) {
                val sourceFile = project.layout.buildDirectory.file("dummy-source.cpp")
                outputFile.set(sourceFile)
                symbolName.set("__" + toSymbol(project.path) + "_" + toSymbol(project.name) + "__")
            }
        }

        private fun toSymbol(s: String): String {
            return s.replace(":", "_").replace("-", "_")
        }
    }

}