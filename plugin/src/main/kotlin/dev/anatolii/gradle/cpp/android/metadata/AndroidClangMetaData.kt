package dev.anatolii.gradle.cpp.android.metadata

import org.gradle.nativeplatform.toolchain.internal.SystemLibraries
import org.gradle.nativeplatform.toolchain.internal.metadata.CompilerMetadata
import org.gradle.util.VersionNumber
import java.io.File

open class AndroidClangMetaData(
        private val scrapedVersion: VersionNumber,
        private val scrapedVendor: String,
        private val systemIncludes: List<File>
) : CompilerMetadata, SystemLibraries {

    override fun getVersion(): VersionNumber {
        return scrapedVersion
    }

    override fun getIncludeDirs(): List<File> {
        return systemIncludes
    }

    override fun getLibDirs(): List<File> {
        return emptyList()
    }

    override fun getPreprocessorMacros(): Map<String, String> {
        return emptyMap()
    }

    override fun getVendor(): String {
        return scrapedVendor
    }

    fun getSystemLibraries(): SystemLibraries = this
}