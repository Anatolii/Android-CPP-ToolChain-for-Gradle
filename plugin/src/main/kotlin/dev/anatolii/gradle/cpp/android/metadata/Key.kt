package dev.anatolii.gradle.cpp.android.metadata

import java.io.File

data class Key internal constructor(
        private val gccBinary: File,
        private val args: List<String>,
        private val path: List<File>,
        private val environmentVariables: Map<String, *>
)