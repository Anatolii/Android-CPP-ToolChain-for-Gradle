package dev.anatolii.gradle.cpp.crossplatform

import org.gradle.internal.Cast
import org.gradle.language.cpp.CppPlatform
import org.gradle.language.cpp.internal.DefaultCppPlatform
import org.gradle.language.nativeplatform.internal.toolchains.DefaultToolChainSelector
import org.gradle.language.nativeplatform.internal.toolchains.ToolChainSelector
import org.gradle.language.swift.SwiftPlatform
import org.gradle.language.swift.SwiftVersion
import org.gradle.language.swift.internal.DefaultSwiftPlatform
import org.gradle.model.internal.registry.ModelRegistry
import org.gradle.nativeplatform.TargetMachine
import org.gradle.nativeplatform.platform.internal.Architectures
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import org.gradle.nativeplatform.platform.internal.DefaultOperatingSystem
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal
import org.gradle.nativeplatform.platform.internal.OperatingSystemInternal
import org.gradle.nativeplatform.toolchain.internal.NativeLanguage
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainRegistryInternal
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider
import org.gradle.nativeplatform.toolchain.internal.ToolType
import org.gradle.util.VersionNumber
import javax.inject.Inject

class CrossPlatformToolChainSelector @Inject
constructor(
        private val modelRegistry: ModelRegistry
) : DefaultToolChainSelector(modelRegistry) {

    override fun <T> select(platformType: Class<T>, requestPlatform: T): ToolChainSelector.Result<T>? {
        return when (platformType) {
            CppPlatform::class.java -> Cast.uncheckedCast(selectCppPlatform(requestPlatform as CppPlatform))
            SwiftPlatform::class.java -> Cast.uncheckedCast(selectSwiftPlatform(requestPlatform as SwiftPlatform))
            else -> throw java.lang.IllegalArgumentException("Unknown type of platform $platformType")
        }
    }

    fun selectCppPlatform(requestPlatform: CppPlatform): ToolChainSelector.Result<CppPlatform> {

        if (targetMatchesHostOsFamilyName(requestPlatform.targetMachine))
            return super.select(requestPlatform.javaClass, requestPlatform)

        val targetNativePlatform = newNativePlatform(requestPlatform.targetMachine)

        // TODO - push all this stuff down to the tool chain and let it create the specific platform and provider

        val sourceLanguage = NativeLanguage.CPP
        val toolChain = getToolChain(sourceLanguage, targetNativePlatform)

        // TODO - don't select again here, as the selection is already performed to select the toolchain
        val toolProvider = toolChain.select(sourceLanguage, targetNativePlatform)

        val targetPlatform = DefaultCppPlatform(requestPlatform.targetMachine, targetNativePlatform)
        return DefaultResult(toolChain, toolProvider, targetPlatform)
    }

    fun selectSwiftPlatform(requestPlatform: SwiftPlatform): ToolChainSelector.Result<SwiftPlatform> {

        if (targetMatchesHostOsFamilyName(requestPlatform.targetMachine))
            return super.select(requestPlatform.javaClass, requestPlatform)

        val targetNativePlatform = newNativePlatform(requestPlatform.targetMachine)

        // TODO - push all this stuff down to the tool chain and let it create the specific platform and provider

        val sourceLanguage = NativeLanguage.SWIFT
        val toolChain = getToolChain(sourceLanguage, targetNativePlatform)

        // TODO - don't select again here, as the selection is already performed to select the toolchain
        val toolProvider = toolChain.select(sourceLanguage, targetNativePlatform)

        var sourceCompatibility: SwiftVersion? = requestPlatform.sourceCompatibility
        if (sourceCompatibility == null && toolProvider.isAvailable) {
            sourceCompatibility = toSwiftVersion(toolProvider.getCompilerMetadata(ToolType.SWIFT_COMPILER).version)
        }
        val targetPlatform = DefaultSwiftPlatform(requestPlatform.targetMachine, sourceCompatibility, targetNativePlatform)
        return DefaultResult(toolChain, toolProvider, targetPlatform)
    }

    private fun targetMatchesHostOsFamilyName(targetMachine: TargetMachine) =
            DefaultNativePlatform.host().operatingSystem.toFamilyName()
                    .equals(targetMachine.operatingSystemFamily.name, ignoreCase = true)

    private fun newNativePlatform(targetMachine: TargetMachine): DefaultNativePlatform {
        return DefaultNativePlatform(targetMachine.operatingSystemFamily.name,
                DefaultOperatingSystem(targetMachine.operatingSystemFamily.name) as OperatingSystemInternal,
                Architectures.forInput(targetMachine.architecture.name))
    }

    private fun getToolChain(sourceLanguage: NativeLanguage, targetNativePlatform: NativePlatformInternal): NativeToolChainInternal {
        val registry = modelRegistry.realize("toolChains", NativeToolChainRegistryInternal::class.java)
        val toolChain = registry.getForPlatform(sourceLanguage, targetNativePlatform)
        toolChain.assertSupported()

        return toolChain
    }

    internal inner class DefaultResult<T>(private val toolChain: NativeToolChainInternal, private val platformToolProvider: PlatformToolProvider, private val targetPlatform: T) : ToolChainSelector.Result<T> {

        override fun getToolChain(): NativeToolChainInternal {
            return toolChain
        }

        override fun getTargetPlatform(): T {
            return targetPlatform
        }

        override fun getPlatformToolProvider(): PlatformToolProvider {
            return platformToolProvider
        }
    }

    companion object {

        internal fun toSwiftVersion(swiftCompilerVersion: VersionNumber): SwiftVersion {
            for (version in SwiftVersion.values()) {
                if (version.version == swiftCompilerVersion.major) {
                    return version
                }
            }
            throw IllegalArgumentException(String.format("Swift language version is unknown for the specified Swift compiler version (%s)", swiftCompilerVersion.toString()))
        }
    }
}
