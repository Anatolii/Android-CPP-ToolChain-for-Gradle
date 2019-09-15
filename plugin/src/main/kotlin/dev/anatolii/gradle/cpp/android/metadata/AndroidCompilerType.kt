package dev.anatolii.gradle.cpp.android.metadata

import org.gradle.nativeplatform.toolchain.internal.metadata.CompilerType

sealed class AndroidCompilerType(
        identifier: String,
        description: String
) : CompilerType {

    private val identifier: String = identifier
    private val description: String = description

    override fun getIdentifier(): String {
        return identifier
    }

    override fun getDescription(): String {
        return description
    }
}

object CLANG : AndroidCompilerType("clang-android", "Android Clang")