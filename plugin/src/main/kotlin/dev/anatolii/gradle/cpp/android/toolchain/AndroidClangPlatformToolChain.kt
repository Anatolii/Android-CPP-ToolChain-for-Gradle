package dev.anatolii.gradle.cpp.android.toolchain

import dev.anatolii.gradle.cpp.android.tool.NdkCommandLineToolConfiguration
import org.gradle.nativeplatform.toolchain.NativePlatformToolChain

interface AndroidClangPlatformToolChain : NativePlatformToolChain {
    /**
     * Returns the settings to use for the C compiler.
     */
    fun getcCompiler(): NdkCommandLineToolConfiguration

    /**
     * Returns the settings to use for the C++ compiler.
     */
    fun getCppCompiler(): NdkCommandLineToolConfiguration

    /**
     * Returns the settings to use for the assembler.
     */
    fun getAssembler(): NdkCommandLineToolConfiguration

    /**
     * Returns the settings to use for the linker.
     */
    fun getLinker(): NdkCommandLineToolConfiguration

    /**
     * Returns the settings to use for the archiver.
     */
    fun getStaticLibArchiver(): NdkCommandLineToolConfiguration
}