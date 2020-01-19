package dev.anatolii.gradle.cpp.android

import dev.anatolii.gradle.cpp.android.ndk.findNdkDirectory
import dev.anatolii.gradle.cpp.android.ndk.findSdkDirectory
import org.gradle.api.Project
import java.io.File

open class CppLibraryAndroid(private val project: Project) {

    companion object {
        internal const val NAME = "libraryAndroid"
    }

    var sdkDir: File? = project.findSdkDirectory()
    var ndkVersion: String? = null
    var ndkDir: File? = null
        get() = field
                ?: project.findNdkDirectory()
                ?: ndkVersion?.let { version -> sdkDir?.let { File(it, "ndk/${version}") } }?.takeIf { it.exists() }
                ?: sdkDir?.let { File(it, "ndk-bundle") }?.takeIf { it.exists() }

    var abis: List<String> = listOf(AndroidInfo.armv7, AndroidInfo.armv8, AndroidInfo.x86, AndroidInfo.x86_64)
    var apis: List<Int> = listOf()
    var isNeon: Boolean = false
    var stl: String = "c++_shared"
    var disableFormatStringChecks: Boolean = false
    var armMode: String = "thumb"
    var forceCppFeatures: Boolean = false
    var cppFeatures: List<String> = listOf()
}