package dev.anatolii.gradle.cpp.crossplatform

import org.gradle.api.Project
import org.gradle.api.internal.attributes.ImmutableAttributesFactory
import org.gradle.language.cpp.plugins.CppApplicationPlugin
import org.gradle.language.internal.NativeComponentFactory
import org.gradle.model.internal.registry.ModelRegistry
import org.gradle.nativeplatform.TargetMachineFactory
import javax.inject.Inject

@Suppress("unused")
class CrossPlatformCppApplicationPlugin @Inject constructor(
        private val componentFactory: NativeComponentFactory,
        private val attributesFactory: ImmutableAttributesFactory,
        private val targetMachineFactory: TargetMachineFactory,
        modelRegistry: ModelRegistry
) : BaseCrossPlatformCppPlugin(attributesFactory, modelRegistry) {

    override fun apply(project: Project) {
        super.apply(project)
        CppApplicationPlugin(componentFactory, toolChainSelector, attributesFactory, targetMachineFactory).apply(project)
    }
}