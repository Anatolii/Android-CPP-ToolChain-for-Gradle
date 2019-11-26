package dev.anatolii.gradle.cpp.android.toolchain

import dev.anatolii.gradle.cpp.android.AndroidInfo
import dev.anatolii.gradle.cpp.android.CppLibraryAndroid
import dev.anatolii.gradle.cpp.android.ndk.NdkToolchainConfig
import org.gradle.api.internal.file.FileResolver
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.os.OperatingSystem
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.work.WorkerLeaseService
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingSchemeFactory
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal
import org.gradle.nativeplatform.toolchain.GccPlatformToolChain
import org.gradle.nativeplatform.toolchain.internal.*
import org.gradle.nativeplatform.toolchain.internal.gcc.DefaultGccPlatformToolChain
import org.gradle.nativeplatform.toolchain.internal.gcc.metadata.GccMetadataProvider
import org.gradle.nativeplatform.toolchain.internal.gcc.metadata.SystemLibraryDiscovery
import org.gradle.nativeplatform.toolchain.internal.metadata.CompilerMetaDataProviderFactory
import org.gradle.nativeplatform.toolchain.internal.tools.CommandLineToolSearchResult
import org.gradle.nativeplatform.toolchain.internal.tools.GccCommandLineToolConfigurationInternal
import org.gradle.nativeplatform.toolchain.internal.tools.ToolSearchPath
import org.gradle.platform.base.internal.toolchain.ToolChainAvailability
import org.gradle.process.internal.ExecActionFactory
import org.slf4j.LoggerFactory

open class AndroidClangToolChain(
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
    private val metaDataProviderFactory = serviceRegistry.get(CompilerMetaDataProviderFactory::class.java)
    private val standardLibraryDiscovery = serviceRegistry.get(SystemLibraryDiscovery::class.java)
    private val metaDataProvider = GccMetadataProvider.forClang(execActionFactory)
    private val llvmToolchainFile = ndkToolchainConfig.llvmToolchainLocation(operatingSystem)


    companion object {
        const val NAME = "Android Clang"
        val platformToolProviders = mutableMapOf<AndroidInfo, PlatformToolProvider>()
        private val LOGGER = LoggerFactory.getLogger(AndroidClangToolChain::class.java)
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

//        addDefaultTools(configurableToolChain)
        ndkToolchainConfig.configure(configurableToolChain, androidInfo, cppLibraryAndroid)
//        val sysrootDir = File(llvmToolchainFile, "sysroot")
//        configurableToolChain.tools.onEach {
//            it.withArguments {
//                add("-stdlib=libc++")
//                add("--target=${androidInfo.targetPrefix}${androidInfo.api}")
//                add("-fno-addrsig")
//                add("-isysroot")
//                add(sysrootDir.path)
//                add("-isystem")
//                add(File(sysrootDir, "usr/include").path)
//                add("-isystem")
//                add(File(sysrootDir, "usr/include/${androidInfo.toolsPrefix}").path)
//                add("-isystem")
//                add(File(sysrootDir, "usr/include/c++/v1").path)
//                add("-isystem")
//                add(File(llvmToolchainFile, "include/c++/4.9.x").path)
//            }
//        }

        configureActions.execute(configurableToolChain)

        val result = ToolChainAvailability()
        initTools(configurableToolChain)
        return if (!result.isAvailable) {
            UnavailablePlatformToolProvider(targetPlatform.operatingSystem, result)
        } else AndroidClangPlatformToolProvider(
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

    private fun initTools(platformToolChain: DefaultGccPlatformToolChain) {
        // Attempt to determine whether the compiler is the correct implementation
        for (tool in platformToolChain.compilers) {
            val compiler = locate(tool)
            if (compiler.isAvailable) {
                val gccMetadata = metaDataProvider.getCompilerMetaData(toolSearchPath.path) {
                    executable(compiler.tool).args(platformToolChain.getCompilerProbeArgs())
                }
                if (!gccMetadata.isAvailable) {
                    return
                }
                // Assume all the other compilers are ok, if they happen to be installed
                LOGGER.debug("Found {} with version {}", tool.toolType.toolName, gccMetadata)
                break
            }
        }
    }

//    private fun addDefaultTools(toolChain: DefaultGccPlatformToolChain) {
//        toolChain.add(DefaultGccCommandLineToolConfiguration(ToolType.C_COMPILER, "clang"))
//        toolChain.add(DefaultGccCommandLineToolConfiguration(ToolType.CPP_COMPILER, "clang++"))
//        toolChain.add(DefaultGccCommandLineToolConfiguration(ToolType.LINKER, "clang++"))
//        toolChain.add(DefaultGccCommandLineToolConfiguration(ToolType.STATIC_LIB_ARCHIVER, "ar"))
//        toolChain.add(DefaultGccCommandLineToolConfiguration(ToolType.ASSEMBLER, "clang"))
//        toolChain.add(DefaultGccCommandLineToolConfiguration(ToolType.SYMBOL_EXTRACTOR, "objcopy"))
//        toolChain.add(DefaultGccCommandLineToolConfiguration(ToolType.STRIPPER, "strip"))
//    }
}