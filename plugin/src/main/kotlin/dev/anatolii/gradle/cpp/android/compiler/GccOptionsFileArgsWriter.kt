/*
 * Copyright 2012 the original author or authors.
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

import org.gradle.internal.process.ArgWriter
import org.gradle.nativeplatform.toolchain.internal.OptionsFileArgsWriter
import java.io.File
import java.util.*

/**
 * Uses an option file for arguments passed to GCC if possible.
 * Certain GCC options do not function correctly when included in an option file, so include these directly on the command line as well.
 */
internal class GccOptionsFileArgsWriter(tempDir: File) : OptionsFileArgsWriter(tempDir) {

    public override fun transformArgs(originalArgs: List<String>, tempDir: File?): List<String> {
        val commandLineOnlyArgs = getCommandLineOnlyArgs(originalArgs)
        val finalArgs = mutableListOf<String>()
        finalArgs.addAll(ArgWriter.argsFileGenerator(File(tempDir, "options.txt"), ArgWriter.unixStyleFactory()).transform(originalArgs))
        finalArgs.addAll(commandLineOnlyArgs)
        return finalArgs
    }

    private fun getCommandLineOnlyArgs(allArgs: List<String>): List<String> {
        val commandLineOnlyArgs = ArrayList(allArgs)
        commandLineOnlyArgs.retainAll(CLI_ONLY_ARGS)
        return commandLineOnlyArgs
    }

    companion object {
        private val CLI_ONLY_ARGS = Arrays.asList("-m32", "-m64")
    }
}
