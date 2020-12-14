package dev.anatolii.gradle.cpp.android

import dev.anatolii.gradle.cpp.android.ndk.findNdkDirectory
import dev.anatolii.gradle.cpp.android.ndk.findSdkDirectory
import org.gradle.api.Project
import java.io.File
import kotlin.properties.Delegates

open class CppLibraryAndroid(private val project: Project) {

    companion object {
        internal const val NAME = "libraryAndroid"
        private val defaultABIs = listOf(AndroidInfo.armv7, AndroidInfo.armv8, AndroidInfo.x86, AndroidInfo.x86_64)
    }

    var sdkDir: File? = project.findSdkDirectory()
    var ndkVersion: String? = null
    var ndkDir: File? = null
        get() = field
                ?: project.findNdkDirectory()
                ?: ndkVersion?.let { version -> sdkDir?.let { File(it, "ndk/${version}") } }?.takeIf { it.exists() }
                ?: sdkDir?.let { File(it, "ndk-bundle") }?.takeIf { it.exists() }

    var abis: List<String> by Delegates.observable(defaultABIs) { _, _, _ ->
        updateAndroidInfos()
    }
    var apis: List<Int> by Delegates.observable(listOf()) { _, _, _ ->
        updateAndroidInfos()
    }
    var isNeon: Boolean = false
    var stl: String = "c++_shared"
    var disableFormatStringChecks: Boolean = false
    var armMode: String = "thumb"
    var forceCppFeatures: Boolean = false
    var cppFeatures: List<String> = listOf()

    private fun updateAndroidInfos() {
        androidInfos.clear()
        apis.distinct().flatMap { api ->
            abis.distinct().map { abi -> api to abi }
        }.map { (api, abi) ->
            AndroidInfo(api, abi)
        }.onEach {
            androidInfos.add(it)
        }
    }

    internal var androidInfos = project.objects.domainObjectContainer(AndroidInfo::class.java)
}