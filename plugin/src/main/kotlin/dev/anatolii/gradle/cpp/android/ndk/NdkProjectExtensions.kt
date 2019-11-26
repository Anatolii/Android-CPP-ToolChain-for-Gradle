package dev.anatolii.gradle.cpp.android.ndk

import org.gradle.api.Project
import java.io.File
import java.util.*

const val localPropertiesFileName = "local.properties"
const val ndkDirPropertyName = "ndk.dir"
const val sdkDirPropertyName = "sdk.dir"
const val androidNdkHomeEnvironmentVariableName = "ANDROID_NDK_HOME"
const val androidSdkHomeEnvironmentVariableName = "ANDROID_HOME"

fun Project.findNdkDirectory(): File? = findInLocalProperties(ndkDirPropertyName)
        ?: System.getenv(androidNdkHomeEnvironmentVariableName)?.let { File(it) }?.takeIf { it.exists() }

fun Project.findSdkDirectory(): File? = findInLocalProperties(sdkDirPropertyName)
        ?: System.getenv(androidSdkHomeEnvironmentVariableName)?.let { File(it) }?.takeIf { it.exists() }

private fun Project.findInLocalProperties(property: String): File? = File(rootDir, localPropertiesFileName)
        .takeIf { it.exists() && it.isFile }
        ?.let { it.bufferedReader() }
        ?.let { reader ->
            Properties()
                    .also { it.load(reader) }
                    .also { reader.close() }
        }?.getProperty(property)
        ?.let { File(it) }
        ?.takeIf { it.exists() }


