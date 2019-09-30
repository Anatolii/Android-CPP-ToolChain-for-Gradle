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
import org.gradle.internal.os.OperatingSystem.current
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.work.WorkerLeaseService
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingSchemeFactory
import org.gradle.nativeplatform.plugins.NativeComponentPlugin
import org.gradle.nativeplatform.toolchain.NativeToolChainRegistry
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainRegistryInternal
import org.gradle.nativeplatform.toolchain.internal.tools.ToolSearchPath
import org.gradle.process.internal.ExecActionFactory
import java.io.File
import javax.inject.Inject

open class AndroidClangCompilerPlugin @Inject constructor(
        private val serviceRegistry: ServiceRegistry
) : Plugin<Project> {

    override fun apply(project: Project) {
        project.pluginManager.apply(NativeComponentPlugin::class.java)
        val ndkDirectory = project.findNdkDirectory()

        val instantiator = serviceRegistry.get(Instantiator::class.java)
        val fileResolver = serviceRegistry.get(FileResolver::class.java)
        val execActionFactory = serviceRegistry.get(ExecActionFactory::class.java)
        val compilerOutputFileNamingSchemeFactory = serviceRegistry.get(CompilerOutputFileNamingSchemeFactory::class.java)
        val buildOperationExecutor = serviceRegistry.get(BuildOperationExecutor::class.java)
        val workerLeaseService = serviceRegistry.get(WorkerLeaseService::class.java)
        val operatingSystem = current()
        val llvmToolchainFile = operatingSystem.androidLlvmToolchainLocationInsideNDK(ndkDirectory)

        val toolChainRegistry = project.extensions.getByType(NativeToolChainRegistry::class.java)

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
        if(toolChainRegistry is NativeToolChainRegistryInternal)
            toolChainRegistry.registerDefaultToolChain(AndroidClangToolChain.NAME, AndroidClang::class.java)
        else toolChainRegistry.register(AndroidClangToolChain.NAME, AndroidClang::class.java)
    }

    private fun OperatingSystem.androidLlvmToolchainLocationInsideNDK(ndkLocation: File): File {
        return when {
            isMacOsX -> nativePrefix
            isLinux -> familyName
            isWindows -> familyName
            else -> throw RuntimeException("Unsupported operating system: ${this.name}")
        }.let {
            "toolchains/llvm/prebuilt/${it}-x86_64"
        }.let { File(ndkLocation, it) }
    }
}