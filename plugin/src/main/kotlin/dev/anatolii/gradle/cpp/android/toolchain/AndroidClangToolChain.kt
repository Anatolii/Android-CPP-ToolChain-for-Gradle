package dev.anatolii.gradle.cpp.android.toolchain

import dev.anatolii.gradle.cpp.android.AndroidInfo
import dev.anatolii.gradle.cpp.android.metadata.AndroidClangMetaData
import dev.anatolii.gradle.cpp.android.metadata.AndroidClangMetaDataProvider
import dev.anatolii.gradle.cpp.android.tool.NdkCommandLineToolConfiguration
import org.gradle.api.Action
import org.gradle.api.internal.file.FileResolver
import org.gradle.internal.Actions
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.os.OperatingSystem
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.work.WorkerLeaseService
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingSchemeFactory
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal
import org.gradle.nativeplatform.toolchain.NativePlatformToolChain
import org.gradle.nativeplatform.toolchain.internal.ExtendableToolChain
import org.gradle.nativeplatform.toolchain.internal.NativeLanguage
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider
import org.gradle.nativeplatform.toolchain.internal.ToolType
import org.gradle.nativeplatform.toolchain.internal.UnavailablePlatformToolProvider
import org.gradle.nativeplatform.toolchain.internal.UnsupportedPlatformToolProvider
import org.gradle.nativeplatform.toolchain.internal.metadata.CompilerMetaDataProvider
import org.gradle.nativeplatform.toolchain.internal.metadata.CompilerType
import org.gradle.nativeplatform.toolchain.internal.tools.CommandLineToolSearchResult
import org.gradle.nativeplatform.toolchain.internal.tools.ToolSearchPath
import org.gradle.platform.base.internal.toolchain.SearchResult
import org.gradle.platform.base.internal.toolchain.ToolChainAvailability
import org.gradle.process.internal.ExecActionFactory
import org.slf4j.LoggerFactory
import java.io.File
import java.util.*
import javax.annotation.Nullable

open class AndroidClangToolChain(
        name: String,
        private val llvmToolchainFile: File,
        buildOperationExecutor: BuildOperationExecutor,
        private val hostOS: OperatingSystem,
        private val fileResolver: FileResolver,
        private val execActionFactory: ExecActionFactory,
        private val compilerOutputFileNamingSchemeFactory: CompilerOutputFileNamingSchemeFactory,
        private val instantiator: Instantiator,
        private val workerLeaseService: WorkerLeaseService,
        private val metaDataProvider: AndroidClangMetaDataProvider,
        private val toolSearchPath: ToolSearchPath
) : ExtendableToolChain<AndroidClangPlatformToolChain>(name,
        buildOperationExecutor,
        hostOS,
        fileResolver
), AndroidClang {

    companion object {
        const val NAME = "Android Clang"
    }


    private fun androidInfoVariants(): List<AndroidInfo> {
        return listOf(
                AndroidInfo.armv7,
                AndroidInfo.armv8,
                AndroidInfo.x86,
                AndroidInfo.x86_64)
                .map { arch ->
                    (21..29).map { api ->
                        AndroidInfo(api, arch)
                    }
                }.flatten()
    }

    override fun getTypeName(): String {
        return "AndroidClang"
    }

    private val LOGGER = LoggerFactory.getLogger(AndroidClangToolChain::class.java)
    private val platformConfigs = ArrayList<TargetAndroidPlatformConfiguration>()
    private val toolProviders = hashMapOf<NativePlatformInternal, PlatformToolProvider?>()
    private var configInsertLocation: Int = 0


    init {
        configInsertLocation = 0

        val llvmToolchainPath = llvmToolchainFile.invariantSeparatorsPath

        androidInfoVariants().forEach { info ->
            target(info.platformName) {
                val m32args = Action<MutableList<String>> {
                    val args = mutableListOf<String>()
                    args.add("-v")
                    args.add("--target=${info.executablePrefix}")
                    args.add("-fno-addrsig")
                    args.add("-isysroot")
                    args.add("${llvmToolchainPath}/sysroot")
                    args.add("-isystem")
                    args.add("${llvmToolchainPath}/include")
                    args.add("-isystem")
                    args.add("${llvmToolchainPath}/sysroot/usr/include")
                    args.add("-isystem")
                    args.add("${llvmToolchainPath}/sysroot/usr/include/${info.sysrootIncludeFolderName}")
                    args.add("-isystem")
                    args.add("${llvmToolchainPath}/sysroot/usr/include/c++/v1")
                    addAll(0, args)
                }
                getCppCompiler().withArguments(m32args)
                getcCompiler().withArguments(m32args)
                getLinker().withArguments(m32args)
                getAssembler().withArguments(m32args)
                path(File(llvmToolchainFile, "${info.sysrootIncludeFolderName}/bin"))
            }
        }
        path(File(llvmToolchainFile, "bin"))
    }

    protected fun locate(tool: NdkCommandLineToolConfiguration): CommandLineToolSearchResult {
        return toolSearchPath.locate(tool.toolType, tool.executable)
    }

    fun getPath(): List<File> {
        return toolSearchPath.path
    }

    fun path(vararg pathEntries: Any) {
        for (path in pathEntries) {
            toolSearchPath.path(resolve(path))
        }
    }

    fun target(platformName: String) {
        target(platformName, Actions.doNothing<NativePlatformToolChain>())
    }

    fun target(platformName: String, action: Action<in AndroidClangPlatformToolChain>) {
        target(DefaultTargetPlatformConfiguration(listOf(platformName), action))
    }

    fun target(platformNames: List<String>, action: Action<in AndroidClangPlatformToolChain>) {
        target(DefaultTargetPlatformConfiguration(platformNames, action))
    }

    private fun target(targetPlatformConfiguration: TargetAndroidPlatformConfiguration) {
        platformConfigs.add(configInsertLocation, targetPlatformConfiguration)
        configInsertLocation++
    }

    fun setTargets(vararg platformNames: String) {
        platformConfigs.clear()
        configInsertLocation = 0
        for (platformName in platformNames) {
            target(platformName)
        }
    }

    override fun select(targetPlatform: NativePlatformInternal): PlatformToolProvider {
        return select(NativeLanguage.ANY, targetPlatform)
    }

    private fun getProviderForPlatform(targetPlatform: NativePlatformInternal): PlatformToolProvider {
        var toolProvider: PlatformToolProvider? = toolProviders.get(targetPlatform)
        if (toolProvider == null) {
            toolProvider = createPlatformToolProvider(targetPlatform)
            toolProviders.put(targetPlatform, toolProvider)
        }
        return toolProvider
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
                // No C++ compiler, complain about it
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
                // No compilers available, complain about the missing C compiler
            }
            else -> return UnsupportedPlatformToolProvider(targetMachine.operatingSystem, String.format("Don't know how to compile language %s.", sourceLanguage))
        }
    }

    private fun createPlatformToolProvider(targetPlatform: NativePlatformInternal): PlatformToolProvider {
        val targetPlatformConfigurationConfiguration = getPlatformConfiguration(targetPlatform)
                ?: return UnsupportedPlatformToolProvider(targetPlatform.operatingSystem, String.format("Don't know how to build for %s.", targetPlatform.displayName))

        val configurableToolChain = instantiator.newInstance(DefaultAndroidClangPlatformToolChain::class.java, targetPlatform)
        addDefaultTools(configurableToolChain)
        targetPlatformConfigurationConfiguration.apply(configurableToolChain)
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
                configurableToolChain.canUseCommandFile(),
                workerLeaseService,
                CompilerMetaDataProviderWithDefaultArgs(configurableToolChain.getCompilerProbeArgs(), metaDataProvider)
        )

    }

    protected fun initTools(platformToolChain: DefaultAndroidClangPlatformToolChain) {
        // Attempt to determine whether the compiler is the correct implementation
        for (tool in platformToolChain.getCompilers()) {
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

    private fun addDefaultTools(toolChain: DefaultAndroidClangPlatformToolChain) {
        toolChain.add(instantiator.newInstance(NdkCommandLineToolConfiguration::class.java, ToolType.C_COMPILER, "clang"))
        toolChain.add(instantiator.newInstance(NdkCommandLineToolConfiguration::class.java, ToolType.CPP_COMPILER, "clang++"))
        toolChain.add(instantiator.newInstance(NdkCommandLineToolConfiguration::class.java, ToolType.LINKER, "clang++"))
        toolChain.add(instantiator.newInstance(NdkCommandLineToolConfiguration::class.java, ToolType.STATIC_LIB_ARCHIVER, "ar"))
        toolChain.add(instantiator.newInstance(NdkCommandLineToolConfiguration::class.java, ToolType.OBJECTIVECPP_COMPILER, "clang++"))
        toolChain.add(instantiator.newInstance(NdkCommandLineToolConfiguration::class.java, ToolType.OBJECTIVEC_COMPILER, "clang"))
        toolChain.add(instantiator.newInstance(NdkCommandLineToolConfiguration::class.java, ToolType.ASSEMBLER, "clang"))
        toolChain.add(instantiator.newInstance(NdkCommandLineToolConfiguration::class.java, ToolType.SYMBOL_EXTRACTOR, "objcopy"))
        toolChain.add(instantiator.newInstance(NdkCommandLineToolConfiguration::class.java, ToolType.STRIPPER, "strip"))
    }

    @Nullable
    protected fun getPlatformConfiguration(targetPlatform: NativePlatformInternal): TargetAndroidPlatformConfiguration? {
        for (platformConfig in platformConfigs) {
            if (platformConfig.supportsPlatform(targetPlatform)) {
                return platformConfig
            }
        }
        return null
    }

    private class DefaultTargetPlatformConfiguration(//TODO this should be a container of platforms
            private val platformNames: Collection<String>,
            private val configurationAction: Action<in AndroidClangPlatformToolChain>
    ) : TargetAndroidPlatformConfiguration {

        override fun supportsPlatform(targetPlatform: NativePlatformInternal): Boolean {
            return platformNames.contains(targetPlatform.architecture.name)
        }

        override fun apply(platformToolChain: DefaultAndroidClangPlatformToolChain) {
            configurationAction.execute(platformToolChain)
        }
    }

    private class CompilerMetaDataProviderWithDefaultArgs(private val compilerProbeArgs: List<String>, private val delegate: CompilerMetaDataProvider<AndroidClangMetaData>) : CompilerMetaDataProvider<AndroidClangMetaData> {

        override fun getCompilerType(): CompilerType = delegate.compilerType

        override fun getCompilerMetaData(searchPath: List<File>, configureAction: Action<in CompilerMetaDataProvider.CompilerExecSpec>): SearchResult<AndroidClangMetaData> {
            return delegate.getCompilerMetaData(searchPath) {
                args(compilerProbeArgs)
                configureAction.execute(this)
            }
        }
    }

}