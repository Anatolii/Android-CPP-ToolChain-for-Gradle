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
import org.gradle.api.GradleException
import org.gradle.api.tasks.WorkResult
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.operations.BuildOperationQueue
import org.gradle.internal.work.WorkerLeaseService
import org.gradle.nativeplatform.internal.StaticLibraryArchiverSpec
import org.gradle.nativeplatform.toolchain.internal.AbstractCompiler
import org.gradle.nativeplatform.toolchain.internal.ArgsTransformer
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolContext
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolInvocation
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolInvocationWorker
import java.io.File
import java.util.*

/**
 * A static library archiver based on the GNU 'ar' utility
 */
class ArStaticLibraryArchiver(buildOperationExecutor: BuildOperationExecutor, commandLineToolInvocationWorker: CommandLineToolInvocationWorker, invocationContext: CommandLineToolContext, workerLeaseService: WorkerLeaseService) : AbstractCompiler<StaticLibraryArchiverSpec>(buildOperationExecutor, commandLineToolInvocationWorker, invocationContext, ArchiverSpecToArguments(), false, workerLeaseService) {

    override fun execute(spec: StaticLibraryArchiverSpec): WorkResult {
        deletePreviousOutput(spec)

        return super.execute(spec)
    }

    private fun deletePreviousOutput(spec: StaticLibraryArchiverSpec) {
        // Need to delete the previous archive, otherwise stale object files will remain
        if (!spec.outputFile.isFile) {
            return
        }
        if (!spec.outputFile.delete()) {
            throw GradleException("Create static archive failed: could not delete previous archive")
        }
    }

    override fun newInvocationAction(spec: StaticLibraryArchiverSpec, args: List<String>): Action<BuildOperationQueue<CommandLineToolInvocation>> {
        val invocation = newInvocation(
                "archiving " + spec.outputFile.name, spec.outputFile.parentFile, args, spec.operationLogger)

        return Action {
            setLogLocation(spec.operationLogger.logLocation)
            add(invocation)
        }
    }

    override fun addOptionsFileArgs(args: List<String>, tempDir: File) {
        // No support for command file
    }

    private class ArchiverSpecToArguments : ArgsTransformer<StaticLibraryArchiverSpec> {
        override fun transform(spec: StaticLibraryArchiverSpec): List<String> {
            val args = ArrayList<String>()
            // -r : Add files to static archive, creating if required
            // -c : Don't write message to standard error when creating archive
            // -s : Create an object file index (equivalent to running 'ranlib')
            args.add("-rcs")
            args.addAll(spec.allArgs)
            args.add(spec.outputFile.absolutePath)
            for (file in spec.objectFiles) {
                args.add(file.absolutePath)
            }
            return args
        }
    }
}
