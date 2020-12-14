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
abstract class AndroidClangCompilerPlugin @Inject constructor(
        private val serviceRegistry: ServiceRegistry,
        private val targetMachineFactory: TargetMachineFactory
) : Plugin<Project> {

    override fun apply(project: Project) {
        project.pluginManager.apply(NativeComponentPlugin::class.java)
        val pluginExtension = project.extensions.create(CppLibraryAndroid.NAME, CppLibraryAndroid::class.java, project)
        setupWith(project)

        val toolChainRegistry = project.extensions.getByType(NativeToolChainRegistry::class.java)

        toolChainRegistry.registerFactory(AndroidClang::class.java) { name ->
            AndroidClangToolChain(name, pluginExtension, serviceRegistry)
        }
        if (toolChainRegistry is NativeToolChainRegistryInternal)
            toolChainRegistry.registerDefaultToolChain(AndroidClangToolChain.NAME, AndroidClang::class.java)
        else toolChainRegistry.register(AndroidClangToolChain.NAME, AndroidClang::class.java)
    }

    private fun setupWith(project: Project) {
        project.pluginManager.withPlugin("cpp-library") {
            project.extensions.getByType(CppLibraryAndroid::class.java)
                    .androidInfos.all {
                        targetMachineFactory
                                .os(DefaultNativePlatform.getCurrentOperatingSystem().toFamilyName())
                                .architecture(name)
                                .let {
                                    project.extensions.getByType(CppLibrary::class.java).targetMachines.add(it)
                                }
                    }

        }
    }
}