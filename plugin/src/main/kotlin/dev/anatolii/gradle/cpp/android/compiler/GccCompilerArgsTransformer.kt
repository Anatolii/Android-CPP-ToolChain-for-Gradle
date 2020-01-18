/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.anatolii.gradle.cpp.android.compiler

import dev.anatolii.gradle.cpp.android.AndroidInfo
import dev.anatolii.gradle.cpp.android.CppLibraryAndroid
import org.gradle.api.GradleException
import org.gradle.nativeplatform.platform.NativePlatform
import org.gradle.nativeplatform.toolchain.internal.ArgsTransformer
import org.gradle.nativeplatform.toolchain.internal.MacroArgsConverter
import org.gradle.nativeplatform.toolchain.internal.NativeCompileSpec
import java.io.File

/**
 * Maps common options for C/C++ compiling with GCC
 */
internal abstract class GccCompilerArgsTransformer<T : NativeCompileSpec>(
        internal val cppLibraryAndroid: CppLibraryAndroid,
        internal val language: String
) : ArgsTransformer<T> {

    override fun transform(spec: T): List<String> {
        val args = mutableListOf<String>()
        addToolSpecificArgs(spec, args)
        addMacroArgs(spec, args)
        addUserArgs(spec, args)
        addIncludeArgs(spec, args)
        return args
    }

    private fun addToolSpecificArgs(spec: T, args: MutableList<String>) {
        val androidInfo = AndroidInfo.fromPlatformName(spec.targetPlatform.architecture.name)
        args.add("-x")
        args.add(language)
        args.add("-c")
        if (spec.isPositionIndependentCode) {
            // nothing to do for now
        }
        if (spec.isDebuggable) {
            args.addAll(listOf("-O0", "-fno-limit-debug-info"))
        } else {
            if (androidInfo?.arch == AndroidInfo.armv7) {
                args.add("-Oz")
            } else {
                args.add("-O2")
            }
            args.add("-DNDEBUG")
        }
    }

    private fun addIncludeArgs(spec: T, args: MutableList<String>) {
        if (!needsStandardIncludes(spec.targetPlatform)) {
            // nothing to include for now
        }

        for (file in spec.includeRoots) {
            args.add("-I")
            args.add(file.canonicalPath)
        }

        for (file in filterOutNonNDK(spec.systemIncludeRoots)) {
            args.add("-isystem")
            args.add(file.canonicalPath)
        }
    }

    private fun addMacroArgs(spec: T, args: MutableList<String>) {
        for (macroArg in MacroArgsConverter().transform(spec.macros)) {
            args.add("-D$macroArg")
        }
    }

    private fun addUserArgs(spec: T, args: MutableList<String>) {
        args.addAll(spec.allArgs)
    }

    protected open fun needsStandardIncludes(targetPlatform: NativePlatform): Boolean {
        return false
    }

    private fun filterOutNonNDK(files: List<File>): List<File> {
        val ndkCanonicalPath = cppLibraryAndroid.ndkDir?.takeIf { it.exists() }?.canonicalPath
                ?: throw GradleException("NDK folder must be specified and exist.")
        return files.filter {
            it.canonicalPath.startsWith(ndkCanonicalPath)
        }
    }
}
