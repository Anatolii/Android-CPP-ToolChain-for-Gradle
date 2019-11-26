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

import org.gradle.api.Action
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.operations.BuildOperationQueue
import org.gradle.internal.work.WorkerLeaseService
import org.gradle.nativeplatform.internal.LinkerSpec
import org.gradle.nativeplatform.internal.SharedLibraryLinkerSpec
import org.gradle.nativeplatform.toolchain.internal.AbstractCompiler
import org.gradle.nativeplatform.toolchain.internal.ArgsTransformer
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolContext
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolInvocation
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolInvocationWorker
import java.io.File
import java.util.*

internal class GccLinker(
        buildOperationExecutor: BuildOperationExecutor,
        commandLineToolInvocationWorker: CommandLineToolInvocationWorker,
        invocationContext: CommandLineToolContext,
        useCommandFile: Boolean,
        workerLeaseService: WorkerLeaseService
) : AbstractCompiler<LinkerSpec>(
        buildOperationExecutor,
        commandLineToolInvocationWorker,
        invocationContext,
        GccLinkerArgsTransformer(),
        useCommandFile,
        workerLeaseService
) {

    override fun newInvocationAction(spec: LinkerSpec, args: List<String>): Action<BuildOperationQueue<CommandLineToolInvocation>> {
        val invocation = newInvocation(
                "linking " + spec.outputFile.name, args, spec.operationLogger)

        return Action {
            setLogLocation(spec.operationLogger.logLocation)
            add(invocation)
        }
    }

    override fun addOptionsFileArgs(args: List<String>, tempDir: File) {
        GccOptionsFileArgsWriter(tempDir).execute(args)
    }

    private class GccLinkerArgsTransformer : ArgsTransformer<LinkerSpec> {
        override fun transform(spec: LinkerSpec): List<String> {
            val args = ArrayList<String>()

            args.addAll(spec.systemArgs)

            if (spec is SharedLibraryLinkerSpec) {
                args.add("-shared")
                maybeSetInstallName(spec, args)
            }
            args.add("-o")
            args.add(spec.outputFile.absolutePath)
            for (file in spec.objectFiles) {
                args.add(file.absolutePath)
            }
            for (file in spec.libraries) {
                args.add(file.absolutePath)
            }
            if (spec.libraryPath.isNotEmpty()) {
                throw UnsupportedOperationException("Library Path not yet supported on GCC")
            }

            for (userArg in spec.args) {
                args.add(userArg)
            }

            return args
        }

        private fun maybeSetInstallName(spec: SharedLibraryLinkerSpec, args: MutableList<String>) {
            val installName = spec.installName
            val targetOs = spec.targetPlatform.operatingSystem

            if (installName == null || targetOs.isWindows) {
                return
            }
            if (targetOs.isMacOsX) {
                args.add("-Wl,-install_name,$installName")
            } else {
                args.add("-Wl,-soname,$installName")
            }
        }
    }
}
