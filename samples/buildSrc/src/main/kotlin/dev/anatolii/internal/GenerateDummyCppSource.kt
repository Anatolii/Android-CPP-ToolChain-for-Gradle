import org.gradle.api.DefaultTask
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.IOException
import java.nio.file.Files

@CacheableTask
open class GenerateDummyCppSource : DefaultTask() {
    @get:Input
    val symbolName = project.objects.property(String::class.java)
    @get:OutputFile
    val outputFile = project.objects.fileProperty()

    @TaskAction
    @Throws(IOException::class)
    private fun doGenerate() {
        val source = "void " + symbolName.get() + "() {}"
        Files.write(outputFile.asFile.get().toPath(), source.toByteArray())
    }
}