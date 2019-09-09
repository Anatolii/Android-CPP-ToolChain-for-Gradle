package dev.anatolii.gradle.cpp.android.ndk

import org.gradle.api.GradleException
import org.gradle.api.Project
import java.io.File
import java.util.*

private const val localPropertiesFileName = "local.properties"
private const val ndkDirPropertyName = "ndk.dir"
private const val androidNdkHomeEnvironmentVariableName = "ANDROID_NDK_HOME"

fun Project.findNdkDirectory(): File = findNdkFile()
        .takeIf { it.exists() && it.isDirectory }
        ?: throw GradleException("Provided NDK location doesn't exist or isn't a directory: ${findNdkFile()}")

private fun Project.findNdkFile(): File = findNdkInLocalProperties()
        ?: System.getenv(androidNdkHomeEnvironmentVariableName)
                ?.let { File(it) }
        ?: throw GradleException("NDK was not found in $localPropertiesFileName file specified as $ndkDirPropertyName. " +
                "Also there's no $androidNdkHomeEnvironmentVariableName environment variable specified.")

private fun Project.findNdkInLocalProperties(): File? = File(rootDir, localPropertiesFileName)
        .takeIf { it.exists() && it.isFile }
        ?.let { it.bufferedReader() }
        ?.let { reader ->
            Properties()
                    .also { it.load(reader) }
                    .also { reader.close() }
        }?.getProperty(ndkDirPropertyName)
        ?.let { File(it) }
        ?.takeIf { it.exists() }


