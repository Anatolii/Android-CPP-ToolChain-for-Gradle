package dev.anatolii.gradle.cpp.android.tool

import org.gradle.nativeplatform.toolchain.internal.ToolType
import org.gradle.nativeplatform.toolchain.internal.tools.DefaultCommandLineToolConfiguration

open class NdkCommandLineToolConfiguration(
        toolType: ToolType,
        var executable: String
) : DefaultCommandLineToolConfiguration(toolType)