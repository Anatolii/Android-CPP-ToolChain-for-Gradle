package dev.anatolii.gradle.cpp.android.ndk

import dev.anatolii.gradle.cpp.android.CppLibraryAndroid
import org.gradle.api.GradleException
import org.gradle.api.logging.Logging
import org.gradle.internal.os.OperatingSystem
import java.io.File
import java.util.*

class NdkToolchainConfig(val cppLibraryAndroid: CppLibraryAndroid) {

    companion object {
        private val logger = Logging.getLogger(NdkToolchainConfig::class.java)
        private fun OperatingSystem.androidLlvmToolchainPrefix(): String {
            return when {
                isMacOsX -> nativePrefix
                isLinux -> familyName
                isWindows -> familyName
                else -> throw RuntimeException("Unsupported operating system: ${this.name}")
            }
        }

        private const val sourcePropertiesFileName = "source.properties"

        fun readPlatformsFile(androidNdk: File): Map<String, String> {
            return File(androidNdk, "build/cmake/platforms.cmake")
                    .readLines()
                    .map {
                        it.substringAfter('(')
                                .substringBefore(')')
                    }.map {
                        it.split(" ").let { split -> split[0] to split[1].replace("\"", "") }
                    }.toMap()
        }

        fun validateSourcePropertiesFileContent(androidNdk: File) {
            val (description, revision) = File(androidNdk, sourcePropertiesFileName)
                    .bufferedReader()
                    .let { reader ->
                        Properties().also { it.load(reader) }.also { reader.close() }
                    }.let { it.getProperty("Pkg.Desc") to it.getProperty("Pkg.Revision") }
            description.takeUnless { it == "Android NDK" }?.let {
                throw GradleException("Failed to parse Android NDK revision. Description is not equals \"Android NDK\"")
            }
            revision.takeUnless { it.matches("^([0-9]+)\\.([0-9]+)\\.([0-9]+)(-beta([0-9]+))?".toRegex()) }?.let {
                throw GradleException("Failed to parse Android NDK revision. Detected revision: $it")
            }
            logger.info("Found Android NDK revision $revision at ${androidNdk.path}")
        }

        fun generateAndroidHostTag(): String = with(OperatingSystem.current()) {
            when {
                isLinux -> "linux-x86_64"
                isUnix -> "linux-x86_64"
                isWindows -> "windows-x86_64"
                isMacOsX -> "darwin-x86_64"
                else -> throw GradleException("Unsupported host OS: $name")
            }
        }
    }

    fun path(operatingSystem: OperatingSystem): List<File> {
        val llvmLocation = llvmToolchainLocation(operatingSystem)
        return listOf(File(llvmLocation, "bin"))
    }

    private fun llvmToolchainLocation(operatingSystem: OperatingSystem) =
            File(cppLibraryAndroid.ndkDir, "toolchains/llvm/prebuilt/${operatingSystem.androidLlvmToolchainPrefix()}-x86_64")


}
