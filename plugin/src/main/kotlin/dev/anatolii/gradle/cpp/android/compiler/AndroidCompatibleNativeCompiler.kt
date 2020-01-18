package dev.anatolii.gradle.cpp.android.compiler

import org.gradle.api.Transformer
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.work.WorkerLeaseService
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingSchemeFactory
import org.gradle.nativeplatform.toolchain.internal.ArgsTransformer
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolContext
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolInvocationWorker
import org.gradle.nativeplatform.toolchain.internal.NativeCompileSpec
import org.gradle.nativeplatform.toolchain.internal.NativeCompiler
import java.io.File

abstract class AndroidCompatibleNativeCompiler<T : NativeCompileSpec>(
        buildOperationExecutor: BuildOperationExecutor,
        compilerOutputFileNamingSchemeFactory: CompilerOutputFileNamingSchemeFactory,
        commandLineTool: CommandLineToolInvocationWorker,
        invocationContext: CommandLineToolContext,
        argsTransformer: ArgsTransformer<T>,
        specTransformer: Transformer<T, T>,
        objectFileExtension: String,
        useCommandFile: Boolean,
        workerLeaseService: WorkerLeaseService
) : NativeCompiler<T>(
        buildOperationExecutor,
        compilerOutputFileNamingSchemeFactory,
        commandLineTool,
        invocationContext,
        argsTransformer,
        specTransformer,
        objectFileExtension,
        useCommandFile,
        workerLeaseService
) {

    override fun getOutputArgs(spec: T, outputFile: File): List<String> {
        return listOf("-o", outputFile.canonicalPath)
    }

    override fun addOptionsFileArgs(args: List<String>, tempDir: File) {
        AndroidClangOptionsFileArgsWriter(tempDir).also { it.execute(args) }
    }

    override fun getPCHArgs(spec: T): List<String> {
        val pchArgs = ArrayList<String>()
        if (spec.prefixHeaderFile != null) {
            pchArgs.add("-include")
            pchArgs.add(spec.prefixHeaderFile.canonicalPath)
        }
        return pchArgs
    }
}
