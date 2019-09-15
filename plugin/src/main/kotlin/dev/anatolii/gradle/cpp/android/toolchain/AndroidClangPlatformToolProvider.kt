package dev.anatolii.gradle.cpp.android.toolchain

import dev.anatolii.gradle.cpp.android.compiler.ArStaticLibraryArchiver
import dev.anatolii.gradle.cpp.android.compiler.Assembler
import dev.anatolii.gradle.cpp.android.compiler.CCompiler
import dev.anatolii.gradle.cpp.android.compiler.CPCHCompiler
import dev.anatolii.gradle.cpp.android.compiler.CppCompiler
import dev.anatolii.gradle.cpp.android.compiler.CppPCHCompiler
import dev.anatolii.gradle.cpp.android.compiler.GccLinker
import dev.anatolii.gradle.cpp.android.metadata.AndroidClangMetaData
import dev.anatolii.gradle.cpp.android.tool.NdkCommandLineToolConfiguration
import dev.anatolii.gradle.cpp.android.tool.ToolRegistry
import org.gradle.api.GradleException
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.work.WorkerLeaseService
import org.gradle.language.base.internal.compile.Compiler
import org.gradle.language.base.internal.compile.DefaultCompilerVersion
import org.gradle.language.base.internal.compile.VersionAwareCompiler
import org.gradle.nativeplatform.internal.BinaryToolSpec
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingSchemeFactory
import org.gradle.nativeplatform.internal.LinkerSpec
import org.gradle.nativeplatform.internal.StaticLibraryArchiverSpec
import org.gradle.nativeplatform.platform.internal.OperatingSystemInternal
import org.gradle.nativeplatform.toolchain.internal.AbstractPlatformToolProvider
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolContext
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolInvocationWorker
import org.gradle.nativeplatform.toolchain.internal.DefaultCommandLineToolInvocationWorker
import org.gradle.nativeplatform.toolchain.internal.DefaultMutableCommandLineToolContext
import org.gradle.nativeplatform.toolchain.internal.EmptySystemLibraries
import org.gradle.nativeplatform.toolchain.internal.OutputCleaningCompiler
import org.gradle.nativeplatform.toolchain.internal.Stripper
import org.gradle.nativeplatform.toolchain.internal.SymbolExtractor
import org.gradle.nativeplatform.toolchain.internal.SystemLibraries
import org.gradle.nativeplatform.toolchain.internal.ToolType
import org.gradle.nativeplatform.toolchain.internal.compilespec.AssembleSpec
import org.gradle.nativeplatform.toolchain.internal.compilespec.CCompileSpec
import org.gradle.nativeplatform.toolchain.internal.compilespec.CppCompileSpec
import org.gradle.nativeplatform.toolchain.internal.compilespec.ObjectiveCCompileSpec
import org.gradle.nativeplatform.toolchain.internal.compilespec.ObjectiveCppCompileSpec
import org.gradle.nativeplatform.toolchain.internal.metadata.CompilerMetaDataProvider
import org.gradle.nativeplatform.toolchain.internal.metadata.CompilerMetadata
import org.gradle.nativeplatform.toolchain.internal.tools.CommandLineToolSearchResult
import org.gradle.nativeplatform.toolchain.internal.tools.ToolSearchPath
import org.gradle.platform.base.internal.toolchain.SearchResult
import org.gradle.process.internal.ExecActionFactory

class AndroidClangPlatformToolProvider(buildOperationExecutor: BuildOperationExecutor,
                                       targetOperatingSystem: OperatingSystemInternal,
                                       private val toolSearchPath: ToolSearchPath,
                                       private val toolRegistry: ToolRegistry,
                                       private val execActionFactory: ExecActionFactory,
                                       private val compilerOutputFileNamingSchemeFactory: CompilerOutputFileNamingSchemeFactory,
                                       private val useCommandFile: Boolean,
                                       private val workerLeaseService: WorkerLeaseService,
                                       private val metaDataProvider: CompilerMetaDataProvider<AndroidClangMetaData>
) : AbstractPlatformToolProvider(buildOperationExecutor, targetOperatingSystem) {

    companion object {
        private val LANGUAGE_FOR_COMPILER = mapOf(
                ToolType.C_COMPILER to "c",
                ToolType.CPP_COMPILER to "c++"
        )
    }

    override fun locateTool(compilerType: ToolType): CommandLineToolSearchResult {
        return toolSearchPath.locate(compilerType, toolRegistry.getTool(compilerType).executable)
    }

    override fun getSystemLibraries(compilerType: ToolType): SystemLibraries {
        val gccMetadata = getGccMetadata(compilerType)
        return if (gccMetadata.isAvailable) {
            gccMetadata.component.getSystemLibraries()
        } else EmptySystemLibraries()
    }

    override fun createCppCompiler(): Compiler<CppCompileSpec> {
        val cppCompilerTool = toolRegistry.getTool(ToolType.CPP_COMPILER)
        val cppCompiler = CppCompiler(buildOperationExecutor, compilerOutputFileNamingSchemeFactory, commandLineTool(cppCompilerTool), context(cppCompilerTool), objectFileExtension, useCommandFile, workerLeaseService)
        val outputCleaningCompiler = OutputCleaningCompiler(cppCompiler, compilerOutputFileNamingSchemeFactory, objectFileExtension)
        return versionAwareCompiler(outputCleaningCompiler, ToolType.CPP_COMPILER)
    }

    override fun createCppPCHCompiler(): Compiler<*> {
        val cppCompilerTool = toolRegistry.getTool(ToolType.CPP_COMPILER)
        val cppPCHCompiler = CppPCHCompiler(buildOperationExecutor, compilerOutputFileNamingSchemeFactory, commandLineTool(cppCompilerTool), context(cppCompilerTool), getPCHFileExtension(), useCommandFile, workerLeaseService)
        val outputCleaningCompiler = OutputCleaningCompiler(cppPCHCompiler, compilerOutputFileNamingSchemeFactory, getPCHFileExtension())
        return versionAwareCompiler(outputCleaningCompiler, ToolType.CPP_COMPILER)
    }

    private fun <T : BinaryToolSpec> versionAwareCompiler(compiler: Compiler<T>, toolType: ToolType): VersionAwareCompiler<T> {
        val gccMetadata = getGccMetadata(toolType)
        return VersionAwareCompiler(compiler, DefaultCompilerVersion(
                metaDataProvider.compilerType.identifier,
                gccMetadata.component.vendor,
                gccMetadata.component.version)
        )
    }

    override fun createCCompiler(): Compiler<CCompileSpec> {
        val cCompilerTool = toolRegistry.getTool(ToolType.C_COMPILER)
        val cCompiler = CCompiler(buildOperationExecutor, compilerOutputFileNamingSchemeFactory, commandLineTool(cCompilerTool), context(cCompilerTool), objectFileExtension, useCommandFile, workerLeaseService)
        val outputCleaningCompiler = OutputCleaningCompiler(cCompiler, compilerOutputFileNamingSchemeFactory, objectFileExtension)
        return versionAwareCompiler(outputCleaningCompiler, ToolType.C_COMPILER)
    }

    override fun createCPCHCompiler(): Compiler<*> {
        val cCompilerTool = toolRegistry.getTool(ToolType.C_COMPILER)
        val cpchCompiler = CPCHCompiler(buildOperationExecutor, compilerOutputFileNamingSchemeFactory, commandLineTool(cCompilerTool), context(cCompilerTool), getPCHFileExtension(), useCommandFile, workerLeaseService)
        val outputCleaningCompiler = OutputCleaningCompiler(cpchCompiler, compilerOutputFileNamingSchemeFactory, getPCHFileExtension())
        return versionAwareCompiler(outputCleaningCompiler, ToolType.C_COMPILER)
    }

    override fun createObjectiveCppCompiler(): Compiler<ObjectiveCppCompileSpec> {
        throw GradleException("Unsupported: ${ToolType.OBJECTIVECPP_COMPILER}")
    }

    override fun createObjectiveCppPCHCompiler(): Compiler<*> {
        throw GradleException("Unsupported: ${ToolType.OBJECTIVECPP_COMPILER}")
    }

    override fun createObjectiveCCompiler(): Compiler<ObjectiveCCompileSpec> {
        throw GradleException("Unsupported: ${ToolType.OBJECTIVEC_COMPILER}")
    }

    override fun createObjectiveCPCHCompiler(): Compiler<*> {
        throw GradleException("Unsupported: ${ToolType.OBJECTIVEC_COMPILER}")
    }

    override fun createAssembler(): Compiler<AssembleSpec> {
        val assemblerTool = toolRegistry.getTool(ToolType.ASSEMBLER)
        // Disable command line file for now because some custom assemblers
        // don't understand the same arguments as GCC.
        return Assembler(buildOperationExecutor, compilerOutputFileNamingSchemeFactory, commandLineTool(assemblerTool), context(assemblerTool), objectFileExtension, false, workerLeaseService)
    }

    override fun createLinker(): Compiler<LinkerSpec> {
        val linkerTool = toolRegistry.getTool(ToolType.LINKER)
        return versionAwareCompiler(GccLinker(buildOperationExecutor, commandLineTool(linkerTool), context(linkerTool), useCommandFile, workerLeaseService), ToolType.LINKER)
    }

    override fun createStaticLibraryArchiver(): Compiler<StaticLibraryArchiverSpec> {
        val staticLibArchiverTool = toolRegistry.getTool(ToolType.STATIC_LIB_ARCHIVER)
        return ArStaticLibraryArchiver(buildOperationExecutor, commandLineTool(staticLibArchiverTool), context(staticLibArchiverTool), workerLeaseService)
    }

    override fun createSymbolExtractor(): Compiler<*> {
        val symbolExtractor = toolRegistry.getTool(ToolType.SYMBOL_EXTRACTOR)
        return SymbolExtractor(buildOperationExecutor, commandLineTool(symbolExtractor), context(symbolExtractor), workerLeaseService)
    }

    override fun createStripper(): Compiler<*> {
        val stripper = toolRegistry.getTool(ToolType.STRIPPER)
        return Stripper(buildOperationExecutor, commandLineTool(stripper), context(stripper), workerLeaseService)
    }

    private fun commandLineTool(tool: NdkCommandLineToolConfiguration): CommandLineToolInvocationWorker {
        val key = tool.toolType
        val exeName = tool.executable
        return DefaultCommandLineToolInvocationWorker(key.toolName, toolSearchPath.locate(key, exeName).tool, execActionFactory)
    }

    private fun context(toolConfiguration: NdkCommandLineToolConfiguration): CommandLineToolContext {
        val baseInvocation = DefaultMutableCommandLineToolContext()
        baseInvocation.addPath(toolSearchPath.path)
        baseInvocation.argAction = toolConfiguration.argAction

        val developerDir = System.getenv("DEVELOPER_DIR")
        if (developerDir != null) {
            baseInvocation.addEnvironmentVar("DEVELOPER_DIR", developerDir)
        }
        return baseInvocation
    }

    private fun getPCHFileExtension(): String {
        return ".h.gch"
    }

    private fun getGccMetadata(compilerType: ToolType): SearchResult<AndroidClangMetaData> {
        val compiler = toolRegistry.getTool(compilerType)
        val searchResult = toolSearchPath.locate(compiler.toolType, compiler.executable)
        val language = LANGUAGE_FOR_COMPILER[compilerType]
        val languageArgs = language?.let { listOf("-x", language) } ?: emptyList()

        return metaDataProvider.getCompilerMetaData(toolSearchPath.path) {
            executable(searchResult.tool).args(languageArgs)
        }
    }


    override fun getCompilerMetadata(toolType: ToolType): CompilerMetadata {
        return getGccMetadata(toolType).component
    }

}