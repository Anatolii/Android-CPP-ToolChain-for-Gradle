/*
 * Copyright 2015 the original author or authors.
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

import dev.anatolii.gradle.cpp.android.CppLibraryAndroid
import org.gradle.internal.Transformers
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.work.WorkerLeaseService
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingSchemeFactory
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolContext
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolInvocationWorker
import org.gradle.nativeplatform.toolchain.internal.compilespec.CppPCHCompileSpec

class CppPCHCompiler(
        cppLibraryAndroid: CppLibraryAndroid,
        buildOperationExecutor: BuildOperationExecutor,
        compilerOutputFileNamingSchemeFactory: CompilerOutputFileNamingSchemeFactory,
        commandLineTool: CommandLineToolInvocationWorker,
        invocationContext: CommandLineToolContext,
        objectFileExtension: String,
        useCommandFile: Boolean,
        workerLeaseService: WorkerLeaseService
) : AndroidCompatibleNativeCompiler<CppPCHCompileSpec>(
        buildOperationExecutor,
        compilerOutputFileNamingSchemeFactory,
        commandLineTool,
        invocationContext,
        CppPCHCompileArgsTransformer(cppLibraryAndroid),
        Transformers.noOpTransformer(),
        objectFileExtension,
        useCommandFile,
        workerLeaseService
) {

    private class CppPCHCompileArgsTransformer(cppLibraryAndroid: CppLibraryAndroid)
        : GccCompilerArgsTransformer<CppPCHCompileSpec>(cppLibraryAndroid,"c++-header")
}
