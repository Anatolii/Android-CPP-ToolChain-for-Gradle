includeBuild(rootProject.projectDir.parentFile)

mapOf(
        ":boost" to "subProjects.list"
).onEach { (projectPath, subProjectsListPath) ->
    include(projectPath)
    File(project(projectPath).projectDir, subProjectsListPath)
            .takeIf { it.exists() }
            ?.readLines()
            ?.forEach { include("${projectPath}:${it}") }

}

include(":icu")

rootProject.children.onEach {
    it.projectDir.takeUnless { file -> file.exists() }?.mkdirs()
}