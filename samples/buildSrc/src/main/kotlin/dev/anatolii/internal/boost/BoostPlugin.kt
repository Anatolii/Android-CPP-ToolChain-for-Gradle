package dev.anatolii.internal.boost

import dev.anatolii.internal.createDummyCppSourceGenerationTask
import groovy.json.JsonSlurper
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.language.cpp.CppLibrary
import java.io.File
import java.net.URL
import java.nio.channels.Channels

@Suppress("unused")
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

        val boostDir = project.file("${project.buildDir}/boost")
        val boostVersionDir = "${boostDir}/${project.version}"
        val gitModulesFile = project.file("$boostVersionDir/gitmodules")
        val packageDataFile = project.file("$boostVersionDir/packageData.json")

        mapOf(
                gitModulesFile to URL("https://raw.githubusercontent.com/boostorg/boost/boost-${project.version}/.gitmodules")
        ).let { downloadFiles(it) }

        writeListOfSubprojects(project, gitModulesFile)
        processPackageDataFile(project, packageDataFile)
    }


    private fun downloadFiles(fileToUrlMap: Map<File, URL>) {
        fileToUrlMap.onEach { (file, url) ->
            file.takeUnless { it.exists() }
                    ?.also {
                        it.parentFile.mkdirs()
                    }
                    ?.writeText(url.readText())
        }
    }

    private fun writeListOfSubprojects(project: Project, gitModulesFile: File) {
        gitModulesFile.readLines()
                .filter { it.contains("submodule ", ignoreCase = true) }
                .map { it.substringAfter('"') }
                .map { it.substringBefore('"') }
                .joinToString(separator = "\n")
                .let { project.file(boostSubProjectsListPath).writeText(it) }

    }

    private fun processPackageDataFile(project: Project, packageDataFile: File) {
        applyResolvedPackageData(
                project,
                resolvePackageData(
                        project,
                        parsePackageDataFile(packageDataFile)
                )
        )
    }

    private fun parsePackageDataFile(packageDataFile: File): Map<String, Any?>? = packageDataFile
            .takeIf { it.exists() }
            ?.let { JsonSlurper().parse(it) }
            ?.let { it as Map<String, Any?> }

    private fun resolvePackageData(project: Project, packageData: Map<String, Any?>?): MutableMap<String, MutableSet<String>> {
        val resolvedPackageData = mutableMapOf<String, MutableSet<String>>()
        packageData?.filterKeys { key -> project.childProjects[key] != null }
                ?.forEach { (moduleName, details) ->
                    (details as Map<String, Any>)[b2RequiresString]
                            .let { it as List<String> }
                            .also { resolvedPackageData[moduleName] = it.toMutableSet() }
                }
        packageData?.filterKeys { key -> resolvedPackageData.keys.contains(key).not() }
                ?.forEach { (circleDependenciesName, details) ->
                    (details as Map<String, Any>)[b2RequiresString]
                            .let { it as List<String> }
                            .also { circleDependencies ->
                                resolvedPackageData.filter {
                                    it.value.contains(circleDependenciesName)
                                }.values.forEach {
                                    it.remove(circleDependenciesName)
                                    it.addAll(circleDependencies)
                                }
                            }
                }
        return resolvedPackageData
    }

    private fun applyResolvedPackageData(project: Project, resolvedPackageData: Map<String, Set<String>>) {
        project.childProjects.forEach { (name, subProject) ->
            registerDownloadUpstreamTask(subProject)
            setupWithUpstreamSources(subProject)
        }
    }

    private fun setupWithUpstreamSources(subProject: Project) {
        upstreamSources(subProject)
                ?: subProject.logger.lifecycle("Run downloadUpstream task to fetch source code of ${subProject.name}")
        applyCppLibrary(subProject)
        val dependenciesFromCMakeFile = dependenciesFromCMakeFile(subProject)
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
            dependenciesFromCMakeFile(nestedProject)
                    .let { nestedProjectDependencies ->
                        nestedProjectDependencies + nestedDependencies(nestedProject, nestedProjectDependencies)
                    }
        }.flatten().toSet()
    }

    private fun customDependencies(project: Project): Set<String> {
        return mapOf(
                "container_hash" to setOf("assert", "config", "core", "detail", "integer", "static_assert", "type_traits"),
                "function_types" to setOf("config", "core", "detail", "mpl", "preprocessor", "type_traits"),
                "io" to setOf("config")
        ).let {
            it[project.name]
        } ?: emptySet()
//        mapOf(
//                "math" to listOf("lexical_cast")
//        )[project.name]
//                ?.forEach { module ->
//                    project.extensions.configure<CppLibrary> {
//                        val currentCppLib = this
//                        project.parent
//                                ?.findProject(":${project.parent?.name}:${module}")
//                                ?.extensions
//                                ?.configure<CppLibrary> {
//                                    val moduleCppLib = this
//                                    currentCppLib.privateHeaders.from(moduleCppLib.publicHeaders)
//                                }
//                    }
//                }
    }

    private fun upstreamSources(subProject: Project): File? =
            subProject.file("${upstreamDir(subProject)}/${subProject.name}-boost-${subProject.version}")
                    .takeIf { it.exists() }

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
                    val generateTask = createDummyCppSourceGenerationTask(project)
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

    private fun dependenciesFromCMakeFile(
            project: Project,
            cmakeListsFileName: String = "CMakeLists.txt"
    ): Set<String> {
        return upstreamSources(project)
                ?.let { project.file("${it}/$cmakeListsFileName") }
                ?.takeIf { it.exists() }
                ?.also { project.logger.debug("has CMake config: ${project.path}") }
                ?.let { fetchDependenciesFromCMakeListsFile(project, it) }
                ?: emptySet()
    }

    private fun fetchDependenciesFromCMakeListsFile(project: Project, cmakeListsFile: File): Set<String> {
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


    private fun registerDownloadUpstreamTask(subProject: Project) {
        val upstreamDir = upstreamDir(subProject)
        val downloadTask = subProject.tasks.register("downloadUpstream") {
            group = subProject.parent?.name
            onlyIf {
                upstreamDir.exists().not() || (upstreamDir.list()?.isEmpty() ?: true)
            }
            outputs.dir(upstreamDir)
            doLast {
                val zipFromRemote = project.file("${upstreamDir}/${subProject.name}.zip")
                zipFromRemote.parentFile.takeUnless { it.exists() }?.mkdirs()

                val remoteZipUrl = URL("https://github.com/boostorg/${subProject.name}/archive/boost-${project.version}.zip")
                val urlChannel = Channels.newChannel(remoteZipUrl.openStream())
                val fileChannel = zipFromRemote.outputStream().channel
                fileChannel.transferFrom(urlChannel, 0, Long.MAX_VALUE)
                urlChannel.close()
                fileChannel.close()

                subProject.copy {
                    from(subProject.zipTree(zipFromRemote))
                    into(upstreamDir)
                }

                zipFromRemote.delete()
            }
        }
        subProject.parent?.tasks?.maybeCreate(downloadTask.name)?.apply {
            dependsOn(downloadTask)
            group = subProject.parent?.name
        }
    }

    private fun upstreamDir(subProject: Project) =
            File(subProject.projectDir, "upstream/${subProject.version}")
}