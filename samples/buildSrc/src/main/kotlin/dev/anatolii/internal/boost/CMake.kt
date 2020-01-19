package dev.anatolii.internal.boost

import org.gradle.api.Project
import java.io.File

class CMake {
    companion object {
        fun dependenciesFromCMakeFile(
                project: Project,
                upstreamSources: File?,
                cmakeListsFileName: String = "CMakeLists.txt"
        ): Set<String> {
            return upstreamSources
                    ?.let { project.file("${it}/$cmakeListsFileName") }
                    ?.takeIf { it.exists() }
                    ?.also { project.logger.debug("has CMake config: ${project.path}") }
                    ?.let { fetchDependenciesFromCMakeListsFile(project, it) }
                    ?: emptySet()
        }

        fun fetchDependenciesFromCMakeListsFile(project: Project, cmakeListsFile: File): Set<String> {
            val targetLinkLibrariesSetup = "\ntarget_link_libraries"
            return cmakeListsFile.readText()
                    .takeIf { it.contains(targetLinkLibrariesSetup) }
                    ?.substringAfter(targetLinkLibrariesSetup)
                    ?.splitToSequence(targetLinkLibrariesSetup)
                    ?.flatMap { targetLibrariesSubstring ->
                        targetLibrariesSubstring.substringAfter("(")
                                .substringBefore(")")
                                .substringAfter(project.name)
                                .substringAfter("INTERFACE")
                                .substringAfter("PUBLIC")
                                .substringAfter("PRIVATE")
                                .lines()
                                .asSequence()
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                                .filterNot { it.startsWith("#") }
                                .filter { it.startsWith("Boost::") }
                                .map { it.substringAfterLast(':') }
                    }
                    ?.also { project.logger.debug("target_link_libraries of ${project.path} :\n${it.joinToString("\n")}\n") }
                    ?.toSet()
                    ?: emptySet()
        }
    }
}