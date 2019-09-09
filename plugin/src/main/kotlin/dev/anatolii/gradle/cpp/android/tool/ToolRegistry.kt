package dev.anatolii.gradle.cpp.android.tool

import org.gradle.nativeplatform.toolchain.internal.ToolType

interface ToolRegistry {
    fun getTool(toolType: ToolType): NdkCommandLineToolConfiguration
}