package dev.anatolii.internal.plugin.upstream

import de.undercouch.gradle.tasks.download.Download
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.TaskProvider

open class UpstreamSources : Plugin<Project> {
    override fun apply(target: Project) {
        target.extensions.create(UpstreamExtension.NAME, UpstreamExtension::class.java, target)
        target.afterEvaluate {
            registerTasksToDownloadUpstreamSources(this)
        }
    }

    private fun registerTasksToDownloadUpstreamSources(project: Project) {
        val upstreamExtension = project.extensions.getByType(UpstreamExtension::class.java)
        val zipFromRemote = project.file("${project.buildDir}/downloads/${project.version}/${project.name}.zip")
                .also { it.parentFile.takeIf { parent -> parent.exists() }?.mkdirs() }

        val downloadUpstreamZip = project.tasks.register("downloadUpstreamZip", Download::class.java) {
            group = upstreamExtension.projectFamilyName
            src(upstreamExtension.sourcesZipUrl)
            dest(zipFromRemote)
            overwrite(false)
        }

        val unzipTask = project.tasks.register("unzipUpstream", Copy::class.java) {
            group = upstreamExtension.projectFamilyName
            onlyIf {
                zipFromRemote.exists()
            }
            dependsOn(downloadUpstreamZip)
            from(project.zipTree(zipFromRemote))
            into(upstreamExtension.extractDir)
            duplicatesStrategy = DuplicatesStrategy.INCLUDE
        }

        val deleteZipFile = project.tasks.register("deleteUpstreamZip", Delete::class.java) {
            group = upstreamExtension.projectFamilyName
            mustRunAfter(unzipTask)
            onlyIf { zipFromRemote.exists() && upstreamExtension.cleanupDownloadedFiles }

            delete(zipFromRemote)
        }

        val downloadTask = project.tasks.register("downloadUpstream") {
            group = upstreamExtension.projectFamilyName
            dependsOn(unzipTask, deleteZipFile)
        }
        registerSameTaskForParentProjects(project, downloadTask)
    }

    private fun registerSameTaskForParentProjects(project: Project, downloadTask: TaskProvider<Task>) {
        project.parent?.also { parent ->
            try {
                parent.tasks.register(downloadTask.name) {
                    dependsOn(downloadTask)
                    group = "upstream"
                }.also { parentDownloadTask ->
                    registerSameTaskForParentProjects(parent, parentDownloadTask)
                }
            } catch (ignore: InvalidUserDataException) {
                project.logger.debug("Project ${parent.path} already has task with name ${downloadTask.name}")
            }
        }
    }

}