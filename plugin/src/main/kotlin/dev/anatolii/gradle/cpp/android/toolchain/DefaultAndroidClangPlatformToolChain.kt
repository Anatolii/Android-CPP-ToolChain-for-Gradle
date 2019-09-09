package dev.anatolii.gradle.cpp.android.toolchain

import dev.anatolii.gradle.cpp.android.tool.NdkCommandLineToolConfiguration
import dev.anatolii.gradle.cpp.android.tool.ToolRegistry
import org.gradle.api.GradleException
import org.gradle.nativeplatform.platform.NativePlatform
import org.gradle.nativeplatform.toolchain.internal.ToolType
import java.util.*

open class DefaultAndroidClangPlatformToolChain(private val nativePlatform: NativePlatform) : AndroidClangPlatformToolChain, ToolRegistry {

    private var canUseCommandFile = true
    private val compilerProbeArgs = ArrayList<String>()
    private val tools = mutableMapOf<ToolType, NdkCommandLineToolConfiguration>()

    override fun getTool(toolType: ToolType): NdkCommandLineToolConfiguration {
        return tools[toolType] ?: throw GradleException("Tool was not registered hence not found: ${toolType}")
    }

    override fun getPlatform(): NativePlatform {
        return nativePlatform
    }


    fun canUseCommandFile(): Boolean {
        return canUseCommandFile
    }

    fun setCanUseCommandFile(canUseCommandFile: Boolean) {
        this.canUseCommandFile = canUseCommandFile
    }

    fun getCompilerProbeArgs(): List<String> {
        return compilerProbeArgs
    }

    fun compilerProbeArgs(vararg args: String) {
        this.compilerProbeArgs.addAll(Arrays.asList(*args))
    }

    fun getTools(): Collection<NdkCommandLineToolConfiguration> {
        return tools.values
    }

    fun getCompilers(): Collection<NdkCommandLineToolConfiguration> {
        return listOf(getTool(ToolType.C_COMPILER), getTool(ToolType.CPP_COMPILER))
    }

    fun add(tool: NdkCommandLineToolConfiguration) {
        tools[tool.toolType] = tool
    }

    override fun getcCompiler(): NdkCommandLineToolConfiguration {
        return getTool(ToolType.C_COMPILER)
    }

    override fun getCppCompiler(): NdkCommandLineToolConfiguration {
        return getTool(ToolType.CPP_COMPILER)
    }

    override fun getAssembler(): NdkCommandLineToolConfiguration {
        return getTool(ToolType.ASSEMBLER)
    }

    override fun getLinker(): NdkCommandLineToolConfiguration {
        return getTool(ToolType.LINKER)
    }

    override fun getStaticLibArchiver(): NdkCommandLineToolConfiguration {
        return getTool(ToolType.STATIC_LIB_ARCHIVER)
    }

    fun getSymbolExtractor(): NdkCommandLineToolConfiguration {
        return getTool(ToolType.SYMBOL_EXTRACTOR)
    }

    fun getStripper(): NdkCommandLineToolConfiguration {
        return getTool(ToolType.STRIPPER)
    }
}