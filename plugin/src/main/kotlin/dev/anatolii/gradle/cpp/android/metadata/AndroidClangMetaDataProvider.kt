package dev.anatolii.gradle.cpp.android.metadata

import org.gradle.nativeplatform.toolchain.internal.metadata.AbstractMetadataProvider
import org.gradle.nativeplatform.toolchain.internal.metadata.CompilerType
import org.gradle.process.internal.ExecActionFactory
import org.gradle.util.VersionNumber
import java.io.File
import java.io.StringReader
import java.util.*
import java.util.regex.Pattern

/**
 * Given a File pointing to an (existing) gcc/g++/clang/clang++ binary, extracts the version number and default architecture by running with -dM -E -v and scraping the output.
 */
class AndroidClangMetaDataProvider internal constructor(execActionFactory: ExecActionFactory,
                                                        private val ndkPath: File,
                                                        private val compilerType: AndroidCompilerType = CLANG
) : AbstractMetadataProvider<AndroidClangMetaData>(execActionFactory) {

    companion object {
        private val DEFINE_PATTERN = Pattern.compile("\\s*#define\\s+(\\S+)\\s+(.*)")
        private val SYSTEM_INCLUDES_START = "#include <...> search starts here:"
        private val SYSTEM_INCLUDES_END = "End of search list."
        private val FRAMEWORK_INCLUDE = " (framework directory)"
    }

    override fun getCompilerType(): CompilerType {
        return compilerType
    }

    override fun compilerArgs(): List<String> {
        return listOf("-dM", "-E", "-v", "-")
    }

    override fun parseCompilerOutput(output: String, error: String, gccBinary: File, path: List<File>): AndroidClangMetaData {
        val defines = parseDefines(output, gccBinary)
        val scrapedVersion = determineVersion(defines, gccBinary)
        val scrapedVendor = determineVendor(error, scrapedVersion, gccBinary)
        val systemIncludes = determineSystemIncludes(error)

        return AndroidClangMetaData(scrapedVersion, scrapedVendor, systemIncludes)
    }

    private fun determineVendor(error: String, versionNumber: VersionNumber, gccBinary: File): String {
        val majorMinorOnly = versionNumber.major.toString() + "." + versionNumber.minor
        return StringReader(error)
                .buffered()
                .useLines { lines ->
                    lines.find { line ->
                        line.contains(majorMinorOnly)
                                && line.contains(" version ")
                                && !line.contains(" default target ")
                    }
                }
                ?: throw BrokenResultException(String.format("Could not determine %s metadata: could not find vendor in output of %s.", compilerType.description, gccBinary))
    }

    private fun determineSystemIncludes(error: String): List<File> {


        val builder = mutableListOf<File>()
        var systemIncludesStarted = false

        StringReader(error).buffered().useLines {
            val iterator = it.iterator()
            while (iterator.hasNext()) {
                val line: String = iterator.next()
                if (SYSTEM_INCLUDES_END == line) {
                    break
                }
                if (SYSTEM_INCLUDES_START == line) {
                    systemIncludesStarted = true
                    continue
                }
                if (systemIncludesStarted) {
                    // Exclude frameworks for CLang - they need to be handled differently
                    if (compilerType == CLANG && line.contains(FRAMEWORK_INCLUDE)) {
                        continue
                    }

                    builder.add(File(line.trim { it <= ' ' }))
                }
            }
            return builder.filter {
                it.path.startsWith(ndkPath.path)
            }.toList()
        }
    }

    private fun parseDefines(output: String, gccBinary: File): Map<String, String> {
        val defines = HashMap<String, String>()
        StringReader(output).buffered().useLines {
            val iterator = it.iterator()
            while (iterator.hasNext()) {
                val line: String = iterator.next()
                val matcher = DEFINE_PATTERN.matcher(line)
                if (!matcher.matches()) {
                    throw BrokenResultException(String.format("Could not determine %s metadata: %s produced unexpected output.", compilerType.description, gccBinary.name))
                }
                defines[matcher.group(1)] = matcher.group(2)
            }
        }


        if (!defines.containsKey("__GNUC__") && !defines.containsKey("__clang__")) {
            throw BrokenResultException(String.format("Could not determine %s metadata: %s produced unexpected output.", compilerType.description, gccBinary.name))
        }
        return defines
    }

    private fun determineVersion(defines: Map<String, String>, gccBinary: File): VersionNumber {
        val major: Int
        val minor: Int
        val patch: Int
        when (compilerType) {
            CLANG -> {
                if (!defines.containsKey("__clang__")) {
                    throw BrokenResultException(String.format("%s appears to be GCC rather than Clang. Treating it as GCC.", gccBinary.name))
                }
                major = toInt(defines["__clang_major__"])
                minor = toInt(defines["__clang_minor__"])
                patch = toInt(defines["__clang_patchlevel__"])
            }
        }
        return VersionNumber(major, minor, patch, null)
    }

    private fun toInt(value: String?): Int {
        if (value == null) {
            return 0
        }
        try {
            return Integer.parseInt(value)
        } catch (e: NumberFormatException) {
            return 0
        }

    }
}
