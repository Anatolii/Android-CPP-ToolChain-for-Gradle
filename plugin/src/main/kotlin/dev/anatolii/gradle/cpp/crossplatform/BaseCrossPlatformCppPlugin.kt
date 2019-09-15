package dev.anatolii.gradle.cpp.crossplatform

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.attributes.ImmutableAttributesFactory
import org.gradle.language.cpp.CppPlatform
import org.gradle.language.cpp.internal.DefaultCppLibrary
import org.gradle.language.cpp.internal.DefaultCppPlatform
import org.gradle.language.cpp.internal.NativeVariantIdentity
import org.gradle.language.nativeplatform.internal.Dimensions
import org.gradle.model.internal.registry.ModelRegistry
import org.gradle.nativeplatform.Linkage
import javax.inject.Inject

open class BaseCrossPlatformCppPlugin @Inject constructor(
        private val attributesFactory: ImmutableAttributesFactory,
        modelRegistry: ModelRegistry
) : Plugin<Project> {

    internal val toolChainSelector = CrossPlatformToolChainSelector(modelRegistry)

    override fun apply(project: Project) {
        project.afterEvaluate(configureDefaultCppLibraryComponents())
    }

    private fun configureDefaultCppLibraryComponents(): (Project).() -> Unit = {
        components.filterIsInstance<DefaultCppLibrary>().forEach { library ->
            Dimensions.libraryVariants(
                    library.baseName,
                    library.linkage,
                    library.targetMachines,
                    objects,
                    attributesFactory,
                    providers.provider { group.toString() },
                    providers.provider { version.toString() },
                    configureNativeVariantIdentity(toolChainSelector, library)
            )
        }
    }

    private fun configureNativeVariantIdentity(
            toolChainSelector: CrossPlatformToolChainSelector,
            library: DefaultCppLibrary
    ): (NativeVariantIdentity).() -> Unit = {
        toolChainSelector.takeIf { isNotHostOrWindowsLinuxMacOS() }
                ?.select(CppPlatform::class.java, DefaultCppPlatform(targetMachine))
                ?.also { result ->
                    if (linkage == Linkage.SHARED) {
                        library.addSharedLibrary(this, result.targetPlatform, result.toolChain, result.platformToolProvider)
                    } else {
                        library.addStaticLibrary(this, result.targetPlatform, result.toolChain, result.platformToolProvider)
                    }
                }
    }

    private fun NativeVariantIdentity.isNotHostOrWindowsLinuxMacOS(): Boolean =
            !Dimensions.tryToBuildOnHost(this) &&
                    !targetMachine.operatingSystemFamily.isLinux &&
                    !targetMachine.operatingSystemFamily.isWindows &&
                    !targetMachine.operatingSystemFamily.isMacOs
}