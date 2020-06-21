package dev.anatolii.internal.plugin.upstream

import org.gradle.api.Project
import java.io.File
import java.net.URL

open class UpstreamExtension constructor(
        project: Project
) {

    var extractDir: File = project.file("${project.projectDir}/upstream/${project.version}")
    var projectFamilyName: String = project.name
    var sourcesZipUrl: URL? = null
    var cleanupDownloadedFiles = false

    companion object {
        const val NAME = "upstream"
    }
}