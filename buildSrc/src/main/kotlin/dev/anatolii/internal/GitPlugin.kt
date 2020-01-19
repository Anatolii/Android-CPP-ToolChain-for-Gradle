package dev.anatolii.internal

import org.gradle.api.Plugin
import org.gradle.api.Project

@Suppress("unused")
abstract class GitPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        project.extensions.create("git", GitRepository::class.java, project.rootDir)
    }
}