package dev.anatolii.gradle.cpp.android.compiler

import org.gradle.internal.process.ArgWriter
import org.gradle.nativeplatform.toolchain.internal.OptionsFileArgsWriter
import java.io.File

/**
 * Uses an option file for arguments passed to GCC if possible.
 * Certain GCC options do not function correctly when included in an option file, so include these directly on the command line as well.
 */
internal class AndroidClangOptionsFileArgsWriter(tempDir: File) : OptionsFileArgsWriter(tempDir) {

    public override fun transformArgs(originalArgs: List<String>, tempDir: File?): List<String> {
        return mutableListOf<String>()
                .also { it.addAll(ArgWriter.argsFileGenerator(File(tempDir, "options.txt"), ArgWriter.unixStyleFactory()).transform(originalArgs)) }
    }

}