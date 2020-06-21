package dev.anatolii.internal.plugin.boost

import de.undercouch.gradle.tasks.download.DownloadAction
import dev.anatolii.internal.DummyCpp
import dev.anatolii.internal.plugin.upstream.UpstreamExtension
import dev.anatolii.internal.plugin.upstream.UpstreamSources
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.language.cpp.CppLibrary
import java.io.File
import java.net.URL

open class BoostPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        if (project.name != "boost") {
            project.logger.error("Plugin applied to a wrong project. Only \"boost\" project is suitable")
            return
        }
        project.buildDir = project.file(boostBuildDirName)

        project.childProjects.forEach { (_, subProject) ->
            subProject.version = project.version
            subProject.group = project.group
        }


        val gitModulesFile = project.file("${project.buildDir}/boost/${project.version}/gitmodules")
        downloadGitModulesFile(project, gitModulesFile)

        writeListOfSubProjects(project, gitModulesFile)
        configureChildProjects(project)
    }

    private fun downloadGitModulesFile(project: Project, destination: File) =
            DownloadAction(project).apply {
                src("https://raw.githubusercontent.com/boostorg/boost/boost-${project.version}/.gitmodules")
                dest(destination)
                overwrite(false)
            }.also {
                it.execute()
            }

    private fun configureChildProjects(project: Project) {
        project.childProjects.forEach { (_, subProject) ->
            registerTasksToDownloadUpstreamSources(subProject)
            setupWithUpstreamSources(subProject)
        }
    }

    private fun setupWithUpstreamSources(subProject: Project) {
        upstreamSources(subProject)
                ?: subProject.logger.lifecycle("Run ${subProject.name}:downloadUpstream task to fetch source code of ${subProject.name}")
        applyCppLibrary(subProject)
        val dependenciesFromCMakeFile = CMake.dependenciesFromCMakeFile(subProject, upstreamSources(subProject))
        val dependencies = dependenciesFromCMakeFile + customDependencies(subProject)
        val nestedDependencies = nestedDependencies(subProject, dependencies)
        applyDependencies(subProject, dependencies)
        applyDependencies(subProject, nestedDependencies)
    }

    private fun nestedDependencies(subProject: Project, dependencies: Set<String>): Set<String> {
        return dependencies.mapNotNull { dependencyProjectName ->
            subProject.parent?.let { parentProject ->
                parentProject.findProject("${parentProject.path}:${dependencyProjectName}")
            }
        }.map { nestedProject ->
            CMake.dependenciesFromCMakeFile(nestedProject, upstreamSources(nestedProject))
                    .let { nestedProjectDependencies ->
                        nestedProjectDependencies + nestedDependencies(nestedProject, nestedProjectDependencies)
                    }
        }.flatten().toSet()
    }

    private fun customDependencies(project: Project): Set<String> {
        return mapOf(
                "container_hash" to setOf("assert", "config", "core", "detail", "integer", "static_assert", "type_traits"),
                "function_types" to setOf("config", "core", "detail", "mpl", "preprocessor", "type_traits"),
                "io" to setOf("config"),
                "locale" to setOf("config")
        ).let {
            it[project.name]
        } ?: emptySet()
    }

    private fun applyCppLibrary(subProject: Project) {
        val sources = upstreamSources(subProject) ?: return

        subProject.pluginManager.apply("cpp-library")
        subProject.pluginManager.apply("dev.anatolii.cpp.android.toolchain")

        subProject.extensions.configure<CppLibrary> {
            source.from("${sources}/src")
            privateHeaders.from(File(sources, "src"))
            publicHeaders.from(File(sources, "include"))
        }

        if (subProject.name == "math") {
            subProject.extensions.getByType(CppLibrary::class.java).privateHeaders.from(File(sources, "src/tr1"))
        }

        if (subProject.name != "compatibility") {
            subProject.extensions.getByType(CppLibrary::class.java)
                    .binaries.configureEach {
                subProject.parent?.findProject(":boost:compatibility")?.let {
                    File(upstreamSources(it), "include/boost/compatibility/cpp_c_headers")
                }?.let { compileTask.orNull?.compilerArgs?.addAll(listOf("-I", "$it")) }
            }
        }

        setupHeadersOnlyLibrary(subProject, sources)

    }

    private fun setupHeadersOnlyLibrary(project: Project, upstreamSources: File) {
        project.file("${upstreamSources}/src")
                .takeUnless { it.exists() }
                ?.apply {
                    val generateTask = DummyCpp.registerSourceGenerationTask(project)
                    project.extensions.configure<CppLibrary> {
                        source.from(generateTask.flatMap { it.outputFile })
                    }
                }
    }

    private fun applyDependencies(project: Project, dependencies: Set<String>?) {
        dependencies?.forEach { dependency ->
            project.parent?.childProjects?.filterKeys { it == dependency }
                    ?.values?.forEach {
                project.extensions.findByType(CppLibrary::class.java)
                        ?.dependencies?.implementation(it)
            }
        }
    }

    private fun registerTasksToDownloadUpstreamSources(subProject: Project) {
        subProject.pluginManager.apply(UpstreamSources::class.java)
        val upstreamExtension = subProject.extensions.getByType(UpstreamExtension::class.java)
        upstreamExtension.apply {
            extractDir = upstreamDir(subProject)
            projectFamilyName = "boost"
            sourcesZipUrl = URL("https://github.com/boostorg/${subProject.name}/archive/boost-${subProject.version}.zip")
        }
    }

    private fun upstreamSources(subProject: Project): File? =
            subProject.file("${upstreamDir(subProject)}/${subProject.name}-boost-${subProject.version}")
                    .takeIf { it.exists() }

    private fun upstreamDir(subProject: Project) =
            File(subProject.projectDir, "upstream/${subProject.version}")

    companion object {

        const val boostBuildDirName = "_build"
        private const val boostSubProjectsListPath = "subProjects.list"

        private fun writeListOfSubProjects(project: Project, gitModulesFile: File) {
            gitModulesFile.readLines()
                    .filter { it.contains("submodule ", ignoreCase = true) }
                    .map { it.substringAfter('"') }
                    .map { it.substringBefore('"') }
                    .joinToString(separator = "\n")
                    .let { project.file(boostSubProjectsListPath).writeText(it) }

        }
    }
}