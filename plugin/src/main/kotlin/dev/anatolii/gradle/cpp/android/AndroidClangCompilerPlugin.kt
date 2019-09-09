package dev.anatolii.gradle.cpp.android

import dev.anatolii.gradle.cpp.android.metadata.AndroidClangMetaDataProvider
import dev.anatolii.gradle.cpp.android.ndk.findNdkDirectory
import dev.anatolii.gradle.cpp.android.toolchain.AndroidClang
import dev.anatolii.gradle.cpp.android.toolchain.AndroidClangToolChain
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.file.FileResolver
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.os.OperatingSystem
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.work.WorkerLeaseService
import org.gradle.model.Defaults
import org.gradle.model.RuleSource
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingSchemeFactory
import org.gradle.nativeplatform.plugins.NativeComponentPlugin
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainRegistryInternal
import org.gradle.nativeplatform.toolchain.internal.tools.ToolSearchPath
import org.gradle.process.internal.ExecActionFactory
import java.io.File

open class AndroidClangCompilerPlugin : Plugin<Project> {

    companion object {
        lateinit var ndkDirectory: File
    }

    override fun apply(project: Project) {
        project.pluginManager.apply(NativeComponentPlugin::class.java)
        ndkDirectory = project.findNdkDirectory()
    }

    @Suppress("unused")
    class Rules : RuleSource() {
        companion object {
            @Defaults
            @JvmStatic
            fun addToolChain(toolChainRegistry: NativeToolChainRegistryInternal, serviceRegistry: ServiceRegistry) {

                val instantiator = serviceRegistry.get(Instantiator::class.java)
                val fileResolver = serviceRegistry.get(FileResolver::class.java)
                val execActionFactory = serviceRegistry.get(ExecActionFactory::class.java)
                val compilerOutputFileNamingSchemeFactory = serviceRegistry.get(CompilerOutputFileNamingSchemeFactory::class.java)
                val buildOperationExecutor = serviceRegistry.get(BuildOperationExecutor::class.java)
                val workerLeaseService = serviceRegistry.get(WorkerLeaseService::class.java)
                val operatingSystem = OperatingSystem.current()
                val llvmToolchainFile = File(ndkDirectory, "toolchains/llvm/prebuilt/${operatingSystem.nativePrefix}-x86_64")

                toolChainRegistry.registerFactory(AndroidClang::class.java) { name ->
                    instantiator.newInstance(
                            AndroidClangToolChain::class.java,
                            name,
                            llvmToolchainFile,
                            buildOperationExecutor,
                            operatingSystem,
                            fileResolver,
                            execActionFactory,
                            compilerOutputFileNamingSchemeFactory,
                            instantiator,
                            workerLeaseService,
                            AndroidClangMetaDataProvider(execActionFactory, ndkDirectory),
                            ToolSearchPath(operatingSystem)
                    )
                }
                toolChainRegistry.registerDefaultToolChain(AndroidClangToolChain.DEFAULT_NAME, AndroidClang::class.java)
            }
        }
    }
}