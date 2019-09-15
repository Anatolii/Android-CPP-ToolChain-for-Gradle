package dev.anatolii.gradle.cpp.android

import dev.anatolii.gradle.cpp.crossplatform.CrossPlatformCppLibraryPlugin
import org.gradle.api.Plugin
import org.gradle.api.internal.project.DefaultProject

open class AndroidCppLibraryPlugin : Plugin<DefaultProject> {

    override fun apply(project: DefaultProject) {
        project.pluginManager.apply(CrossPlatformCppLibraryPlugin::class.java)
        project.pluginManager.apply(AndroidClangCompilerPlugin::class.java)
    }
}