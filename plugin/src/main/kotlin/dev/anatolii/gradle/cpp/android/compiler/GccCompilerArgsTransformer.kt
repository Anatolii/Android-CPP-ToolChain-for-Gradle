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

import org.gradle.nativeplatform.platform.NativePlatform
import org.gradle.nativeplatform.toolchain.internal.ArgsTransformer
import org.gradle.nativeplatform.toolchain.internal.MacroArgsConverter
import org.gradle.nativeplatform.toolchain.internal.NativeCompileSpec

/**
 * Maps common options for C/C++ compiling with GCC
 */
internal abstract class GccCompilerArgsTransformer<T : NativeCompileSpec>(internal val language: String) : ArgsTransformer<T> {

    override fun transform(spec: T): List<String> {
        val args = mutableListOf<String>()
        addToolSpecificArgs(spec, args)
        addMacroArgs(spec, args)
        addUserArgs(spec, args)
        addIncludeArgs(spec, args)
        return args
    }

    protected fun addToolSpecificArgs(spec: T, args: MutableList<String>) {
        args.add("-x")
        args.add(language)
        args.add("-c")
        if (spec.isPositionIndependentCode) {
            // nothing to do
        } else {
            args.add("-static-libstdc++")
        }
        if (spec.isDebuggable) {
            args.addAll(listOf("-O0", "-fno-limit-debug-info"))
        }else {
            args.addAll(listOf("-O2", "-DNDEBUG"))
        }
    }

    protected fun addIncludeArgs(spec: T, args: MutableList<String>) {
        if (!needsStandardIncludes(spec.targetPlatform)) {
            args.add("-nostdinc")
        }

        for (file in spec.includeRoots) {
            args.add("-I")
            args.add(file.absolutePath)
        }

        for (file in spec.systemIncludeRoots) {
            args.add("-isystem")
            args.add(file.absolutePath)
        }
    }

    protected fun addMacroArgs(spec: T, args: MutableList<String>) {
        for (macroArg in MacroArgsConverter().transform(spec.macros)) {
            args.add("-D$macroArg")
        }
    }

    protected fun addUserArgs(spec: T, args: MutableList<String>) {
        args.addAll(spec.allArgs)
    }

    protected open fun needsStandardIncludes(targetPlatform: NativePlatform): Boolean {
        return false
    }

}
