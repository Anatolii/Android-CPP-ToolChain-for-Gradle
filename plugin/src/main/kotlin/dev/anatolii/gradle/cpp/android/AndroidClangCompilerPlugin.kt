package dev.anatolii.gradle.cpp.android

import dev.anatolii.gradle.cpp.android.toolchain.AndroidClang
import dev.anatolii.gradle.cpp.android.toolchain.AndroidClangToolChain
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.internal.service.ServiceRegistry
import org.gradle.language.cpp.CppLibrary
import org.gradle.nativeplatform.TargetMachineFactory
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import org.gradle.nativeplatform.plugins.NativeComponentPlugin
import org.gradle.nativeplatform.toolchain.NativeToolChainRegistry
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainRegistryInternal
import javax.inject.Inject

@Suppress("unused")
open class AndroidClangCompilerPlugin @Inject constructor(
        private val serviceRegistry: ServiceRegistry,
        private val targetMachineFactory: TargetMachineFactory
) : Plugin<Project> {

    override fun apply(project: Project) {
        project.pluginManager.apply(NativeComponentPlugin::class.java)
        val pluginExtension = CppLibraryAndroid(project)
        project.extensions.add(CppLibraryAndroid::class.java, CppLibraryAndroid.NAME, pluginExtension)
        setupWith(project, pluginExtension)

        AndroidClangToolChain.platformToolProviders.clear()
        val toolChainRegistry = project.extensions.getByType(NativeToolChainRegistry::class.java)

        toolChainRegistry.registerFactory(AndroidClang::class.java) { name ->
            AndroidClangToolChain(
                    name,
                    pluginExtension,
                    serviceRegistry
            )
        }
        if (toolChainRegistry is NativeToolChainRegistryInternal)
            toolChainRegistry.registerDefaultToolChain(AndroidClangToolChain.NAME, AndroidClang::class.java)
        else toolChainRegistry.register(AndroidClangToolChain.NAME, AndroidClang::class.java)
    }

    private fun setupWith(project: Project, cppLibraryAndroid: CppLibraryAndroid) {
        project.beforeEvaluate {
            extensions.configure(CppLibrary::class.java) {
                cppLibraryAndroid.apis.flatMap { api ->
                    cppLibraryAndroid.abis.map { abi -> api to abi }
                }.map { (api, abi) ->
                    AndroidInfo(api, abi)
                }.map {
                    targetMachineFactory
                            .os(DefaultNativePlatform.getCurrentOperatingSystem().toFamilyName())
                            .architecture(it.platformName)
                }.forEach {
                    targetMachines.add(it)
                }
            }
        }
    }
}