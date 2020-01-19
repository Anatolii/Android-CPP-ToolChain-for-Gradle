package dev.anatolii.gradle.cpp.android.toolchain

import dev.anatolii.gradle.cpp.android.AndroidInfo
import dev.anatolii.gradle.cpp.android.CppLibraryAndroid
import dev.anatolii.gradle.cpp.android.ndk.NdkToolchainConfig
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.logging.Logging
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.os.OperatingSystem
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.work.WorkerLeaseService
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingSchemeFactory
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal
import org.gradle.nativeplatform.toolchain.GccPlatformToolChain
import org.gradle.nativeplatform.toolchain.internal.ExtendableToolChain
import org.gradle.nativeplatform.toolchain.internal.NativeLanguage
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider
import org.gradle.nativeplatform.toolchain.internal.ToolType
import org.gradle.nativeplatform.toolchain.internal.UnavailablePlatformToolProvider
import org.gradle.nativeplatform.toolchain.internal.UnsupportedPlatformToolProvider
import org.gradle.nativeplatform.toolchain.internal.gcc.DefaultGccPlatformToolChain
import org.gradle.nativeplatform.toolchain.internal.gcc.metadata.GccMetadata
import org.gradle.nativeplatform.toolchain.internal.gcc.metadata.GccMetadataProvider
import org.gradle.nativeplatform.toolchain.internal.tools.CommandLineToolSearchResult
import org.gradle.nativeplatform.toolchain.internal.tools.GccCommandLineToolConfigurationInternal
import org.gradle.nativeplatform.toolchain.internal.tools.ToolSearchPath
import org.gradle.platform.base.internal.toolchain.SearchResult
import org.gradle.platform.base.internal.toolchain.ToolChainAvailability
import org.gradle.process.internal.ExecActionFactory

open class AndroidClangToolChain @JvmOverloads constructor(
        name: String,
        private val cppLibraryAndroid: CppLibraryAndroid,
        private val serviceRegistry: ServiceRegistry,
        buildOperationExecutor: BuildOperationExecutor = serviceRegistry.get(BuildOperationExecutor::class.java),
        hostOS: OperatingSystem = OperatingSystem.current(),
        fileResolver: FileResolver = serviceRegistry.get(FileResolver::class.java)
) : ExtendableToolChain<GccPlatformToolChain>(
        name,
        buildOperationExecutor,
        hostOS,
        fileResolver
), AndroidClang {
    private val ndkToolchainConfig = NdkToolchainConfig(cppLibraryAndroid)
    private val execActionFactory = serviceRegistry.get(ExecActionFactory::class.java)
    private val compilerOutputFileNamingSchemeFactory = serviceRegistry.get(CompilerOutputFileNamingSchemeFactory::class.java)
    private val workerLeaseService: WorkerLeaseService = serviceRegistry.get(WorkerLeaseService::class.java)
    private val toolSearchPath = ToolSearchPath(operatingSystem).also {
        it.path = ndkToolchainConfig.path(operatingSystem)
    }
    private val metaDataProvider = GccMetadataProvider.forClang(execActionFactory)
    private val platformToolProviders: MutableMap<AndroidInfo, PlatformToolProvider> = mutableMapOf()

    companion object {
        const val NAME = "Android Clang"
        private val compilerData: MutableMap<String, SearchResult<GccMetadata>> = mutableMapOf()
        private val logger = Logging.getLogger(AndroidClangToolChain::class.java)
    }

    override fun getTypeName(): String {
        return "Android Clang"
    }

    private fun locate(tool: GccCommandLineToolConfigurationInternal): CommandLineToolSearchResult {
        return toolSearchPath.locate(tool.toolType, tool.executable)
    }

    override fun select(targetPlatform: NativePlatformInternal): PlatformToolProvider {
        return select(NativeLanguage.ANY, targetPlatform)
    }

    override fun select(sourceLanguage: NativeLanguage, targetMachine: NativePlatformInternal): PlatformToolProvider {
        val toolProvider = getProviderForPlatform(targetMachine)
        when (sourceLanguage) {
            NativeLanguage.CPP -> {
                if (toolProvider is UnsupportedPlatformToolProvider) {
                    return toolProvider
                }
                val cppCompiler = toolProvider.locateTool(ToolType.CPP_COMPILER)
                return if (cppCompiler.isAvailable) {
                    toolProvider
                } else UnavailablePlatformToolProvider(targetMachine.operatingSystem, cppCompiler)
            }
            NativeLanguage.ANY -> {
                if (toolProvider is UnsupportedPlatformToolProvider) {
                    return toolProvider
                }
                val cCompiler = toolProvider.locateTool(ToolType.C_COMPILER)
                if (cCompiler.isAvailable) {
                    return toolProvider
                }
                var compiler = toolProvider.locateTool(ToolType.CPP_COMPILER)
                if (compiler.isAvailable) {
                    return toolProvider
                }
                compiler = toolProvider.locateTool(ToolType.OBJECTIVEC_COMPILER)
                if (compiler.isAvailable) {
                    return toolProvider
                }
                compiler = toolProvider.locateTool(ToolType.OBJECTIVECPP_COMPILER)
                return if (compiler.isAvailable) {
                    toolProvider
                } else UnavailablePlatformToolProvider(targetMachine.operatingSystem, cCompiler)
            }
            else -> return UnsupportedPlatformToolProvider(targetMachine.operatingSystem, String.format("Don't know how to compile language %s.", sourceLanguage))
        }
    }

    private fun getProviderForPlatform(targetPlatform: NativePlatformInternal): PlatformToolProvider {
        val androidInfo = AndroidInfo.fromPlatformName(targetPlatform.architecture.name)
                ?: return UnsupportedPlatformToolProvider(targetPlatform.operatingSystem, String.format("Don't know how to build for %s.", targetPlatform.displayName))

        return platformToolProviders.getOrPut(androidInfo) { createPlatformToolProvider(targetPlatform, androidInfo) }
    }

    private fun createPlatformToolProvider(targetPlatform: NativePlatformInternal, androidInfo: AndroidInfo): PlatformToolProvider {
        val configurableToolChain = DefaultGccPlatformToolChain(targetPlatform)

        configurableToolChain.configure(ndkToolchainConfig, androidInfo)

        configureActions.execute(configurableToolChain)

        val result = ToolChainAvailability()
        initTools(configurableToolChain, result)
        return if (!result.isAvailable) {
            UnavailablePlatformToolProvider(targetPlatform.operatingSystem, result)
        } else AndroidClangPlatformToolProvider(
                cppLibraryAndroid,
                buildOperationExecutor,
                targetPlatform.operatingSystem,
                toolSearchPath,
                configurableToolChain,
                execActionFactory,
                compilerOutputFileNamingSchemeFactory,
                workerLeaseService,
                metaDataProvider
        )
    }

    private fun initTools(platformToolChain: DefaultGccPlatformToolChain, availability: ToolChainAvailability) {
        // Attempt to determine whether the compiler is the correct implementation
        for (tool in platformToolChain.compilers) {
            val compiler = locate(tool)
            if (compiler.isAvailable) {
                val gccMetadata = compilerData.getOrPut(toolSearchPath.path.joinToString { it.path }) {
                    metaDataProvider.getCompilerMetaData(toolSearchPath.path) {
                        executable(compiler.tool).args(platformToolChain.compilerProbeArgs)
                    }
                }
                availability.mustBeAvailable(gccMetadata)
                if (!gccMetadata.isAvailable) {
                    return
                }
                // Assume all the other compilers are ok, if they happen to be installed
                logger.debug("Found {} with version {}", tool.toolType.toolName, gccMetadata)
                break
            }
        }
    }
}