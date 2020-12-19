package dev.anatolii.gradle.cpp.android.toolchain

import dev.anatolii.gradle.cpp.android.CppLibraryAndroid
import dev.anatolii.gradle.cpp.android.compiler.*
import org.gradle.api.GradleException
import org.gradle.internal.logging.text.TreeFormatter
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
import org.gradle.nativeplatform.toolchain.internal.*
import org.gradle.nativeplatform.toolchain.internal.compilespec.*
import org.gradle.nativeplatform.toolchain.internal.gcc.DefaultGccPlatformToolChain
import org.gradle.nativeplatform.toolchain.internal.gcc.metadata.GccMetadata
import org.gradle.nativeplatform.toolchain.internal.gcc.metadata.GccMetadataProvider
import org.gradle.nativeplatform.toolchain.internal.metadata.CompilerMetadata
import org.gradle.nativeplatform.toolchain.internal.tools.CommandLineToolSearchResult
import org.gradle.nativeplatform.toolchain.internal.tools.GccCommandLineToolConfigurationInternal
import org.gradle.nativeplatform.toolchain.internal.tools.ToolSearchPath
import org.gradle.platform.base.internal.toolchain.SearchResult
import org.gradle.process.internal.ExecActionFactory
import java.io.File

class AndroidClangPlatformToolProvider(
    private val cppLibraryAndroid: CppLibraryAndroid,
    buildOperationExecutor: BuildOperationExecutor,
    targetOperatingSystem: OperatingSystemInternal,
    private val toolSearchPath: ToolSearchPath,
    private val toolRegistry: DefaultGccPlatformToolChain,
    private val execActionFactory: ExecActionFactory,
    private val compilerOutputFileNamingSchemeFactory: CompilerOutputFileNamingSchemeFactory,
    private val workerLeaseService: WorkerLeaseService,
    private val metaDataProvider: GccMetadataProvider
) : AbstractPlatformToolProvider(buildOperationExecutor, targetOperatingSystem) {

    companion object {
        private val LANGUAGE_FOR_COMPILER = mapOf(
            ToolType.C_COMPILER to "c",
            ToolType.CPP_COMPILER to "c++"
        )
    }

    override fun getExecutableName(executablePath: String): String = executablePath

    override fun getSharedLibraryName(libraryPath: String): String =
        File(super.getSharedLibraryName(libraryPath))
            .let { File(it.parentFile, "${it.nameWithoutExtension}.so") }
            .path

    override fun getObjectFileExtension(): String = ".o"

    override fun getSharedLibraryLinkFileName(libraryPath: String): String {
        return getSharedLibraryName(libraryPath)
    }

    override fun getStaticLibraryName(libraryPath: String): String =
        File(super.getStaticLibraryName(libraryPath))
            .let { File(it.parentFile, "${it.nameWithoutExtension}.a") }
            .path

    override fun locateTool(compilerType: ToolType): CommandLineToolSearchResult {
        return toolSearchPath.locate(compilerType, toolRegistry.getTool(compilerType)?.executable)
    }

    override fun getSystemLibraries(compilerType: ToolType): SystemLibraries {
        val gccMetadata = getGccMetadata(compilerType)
        return if (gccMetadata.isAvailable) {
            gccMetadata.component.systemLibraries
        } else EmptySystemLibraries()
    }

    override fun createCppCompiler(): Compiler<CppCompileSpec> {
        val cppCompilerTool = toolRegistry.getTool(ToolType.CPP_COMPILER)
        val cppCompiler = CppCompiler(
            cppLibraryAndroid,
            buildOperationExecutor,
            compilerOutputFileNamingSchemeFactory,
            commandLineTool(cppCompilerTool),
            context(cppCompilerTool),
            objectFileExtension,
            toolRegistry.isCanUseCommandFile,
            workerLeaseService
        )
        val outputCleaningCompiler =
            OutputCleaningCompiler(cppCompiler, compilerOutputFileNamingSchemeFactory, objectFileExtension)
        return versionAwareCompiler(outputCleaningCompiler, ToolType.CPP_COMPILER)
    }

    override fun createCppPCHCompiler(): Compiler<*> {
        val cppCompilerTool = toolRegistry.getTool(ToolType.CPP_COMPILER)
        val cppPCHCompiler = CppPCHCompiler(
            cppLibraryAndroid,
            buildOperationExecutor,
            compilerOutputFileNamingSchemeFactory,
            commandLineTool(cppCompilerTool),
            context(cppCompilerTool),
            getPCHFileExtension(),
            toolRegistry.isCanUseCommandFile,
            workerLeaseService
        )
        val outputCleaningCompiler =
            OutputCleaningCompiler(cppPCHCompiler, compilerOutputFileNamingSchemeFactory, getPCHFileExtension())
        return versionAwareCompiler(outputCleaningCompiler, ToolType.CPP_COMPILER)
    }

    private fun <T : BinaryToolSpec> versionAwareCompiler(
        compiler: Compiler<T>,
        toolType: ToolType
    ): VersionAwareCompiler<T> {
        val gccMetadata = getGccMetadata(toolType)
        return gccMetadata.takeIf { it.isAvailable }
            ?.let {
                VersionAwareCompiler(
                    compiler,
                    DefaultCompilerVersion(
                        metaDataProvider.compilerType.identifier,
                        it.component.vendor,
                        it.component.version
                    )
                )
            } ?: gccMetadata.let { metadata -> TreeFormatter().also { metadata.explain(it) }.toString() }
            .let { throw GradleException(it) }
    }

    override fun createCCompiler(): Compiler<CCompileSpec> {
        val cCompilerTool = toolRegistry.getTool(ToolType.C_COMPILER)
        val cCompiler = CCompiler(
            cppLibraryAndroid,
            buildOperationExecutor,
            compilerOutputFileNamingSchemeFactory,
            commandLineTool(cCompilerTool),
            context(cCompilerTool),
            objectFileExtension,
            toolRegistry.isCanUseCommandFile,
            workerLeaseService
        )
        val outputCleaningCompiler =
            OutputCleaningCompiler(cCompiler, compilerOutputFileNamingSchemeFactory, objectFileExtension)
        return versionAwareCompiler(outputCleaningCompiler, ToolType.C_COMPILER)
    }

    override fun createCPCHCompiler(): Compiler<*> {
        val cCompilerTool = toolRegistry.getTool(ToolType.C_COMPILER)
        val cpchCompiler = CPCHCompiler(
            cppLibraryAndroid,
            buildOperationExecutor,
            compilerOutputFileNamingSchemeFactory,
            commandLineTool(cCompilerTool),
            context(cCompilerTool),
            getPCHFileExtension(),
            toolRegistry.isCanUseCommandFile,
            workerLeaseService
        )
        val outputCleaningCompiler =
            OutputCleaningCompiler(cpchCompiler, compilerOutputFileNamingSchemeFactory, getPCHFileExtension())
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
        return Assembler(
            cppLibraryAndroid,
            buildOperationExecutor,
            compilerOutputFileNamingSchemeFactory,
            commandLineTool(assemblerTool),
            context(assemblerTool),
            objectFileExtension,
            false,
            workerLeaseService
        )
    }

    override fun createLinker(): Compiler<LinkerSpec> {
        val linkerTool = toolRegistry.getTool(ToolType.LINKER)
        return versionAwareCompiler(
            GccLinker(
                buildOperationExecutor,
                commandLineTool(linkerTool),
                context(linkerTool),
                toolRegistry.isCanUseCommandFile,
                workerLeaseService
            ), ToolType.LINKER
        )
    }

    override fun createStaticLibraryArchiver(): Compiler<StaticLibraryArchiverSpec> {
        val staticLibArchiverTool = toolRegistry.getTool(ToolType.STATIC_LIB_ARCHIVER)
        return ArStaticLibraryArchiver(
            buildOperationExecutor,
            commandLineTool(staticLibArchiverTool),
            context(staticLibArchiverTool),
            workerLeaseService
        )
    }

    override fun createSymbolExtractor(): Compiler<*> {
        val symbolExtractor = toolRegistry.getTool(ToolType.SYMBOL_EXTRACTOR)
        return SymbolExtractor(
            buildOperationExecutor,
            commandLineTool(symbolExtractor),
            context(symbolExtractor),
            workerLeaseService
        )
    }

    override fun createStripper(): Compiler<*> {
        val stripper = toolRegistry.getTool(ToolType.STRIPPER)
        return Stripper(buildOperationExecutor, commandLineTool(stripper), context(stripper), workerLeaseService)
    }

    private fun commandLineTool(tool: GccCommandLineToolConfigurationInternal?): CommandLineToolInvocationWorker {
        return tool?.let {
            val toolType = it.toolType
            val exeName = it.executable
            DefaultCommandLineToolInvocationWorker(
                toolType.toolName,
                toolSearchPath.locate(toolType, exeName).tool,
                execActionFactory
            )
        } ?: throw Exception("Commandline tool configuration is null. Could not create the command line tool invocation worker.")
    }

    private fun context(toolConfiguration: GccCommandLineToolConfigurationInternal?): CommandLineToolContext {
        val baseInvocation = DefaultMutableCommandLineToolContext()
        baseInvocation.addPath(toolSearchPath.path)
        baseInvocation.argAction = toolConfiguration?.argAction

        val developerDir = System.getenv("DEVELOPER_DIR")
        if (developerDir != null) {
            baseInvocation.addEnvironmentVar("DEVELOPER_DIR", developerDir)
        }
        return baseInvocation
    }

    private fun getPCHFileExtension(): String {
        return ".h.gch"
    }

    private fun getGccMetadata(compilerType: ToolType): SearchResult<GccMetadata> {
        val compiler = toolRegistry.getTool(compilerType)
        val searchResult = toolSearchPath.locate(compiler?.toolType, compiler?.executable)
        val language = LANGUAGE_FOR_COMPILER[compilerType]
        val languageArgs = language?.let { listOf("-x", language) } ?: emptyList()

        return searchResult.takeIf { it.isAvailable }
            ?.let {
                metaDataProvider.getCompilerMetaData(toolSearchPath.path) {
                    executable(searchResult.tool).args(
                        languageArgs
                    )
                }
            } ?: searchResult.let { result -> TreeFormatter().also { result.explain(it) }.toString() }
            .let { throw GradleException(it) }
    }

    override fun getCompilerMetadata(toolType: ToolType): CompilerMetadata {
        return getGccMetadata(toolType).component
    }

}