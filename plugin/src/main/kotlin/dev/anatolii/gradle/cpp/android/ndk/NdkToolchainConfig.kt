package dev.anatolii.gradle.cpp.android.ndk

import dev.anatolii.gradle.cpp.android.AndroidInfo
import dev.anatolii.gradle.cpp.android.CppLibraryAndroid
import org.gradle.api.GradleException
import org.gradle.api.logging.Logging
import org.gradle.internal.os.OperatingSystem
import org.gradle.nativeplatform.toolchain.internal.ToolType
import org.gradle.nativeplatform.toolchain.internal.gcc.DefaultGccPlatformToolChain
import org.gradle.nativeplatform.toolchain.internal.tools.DefaultGccCommandLineToolConfiguration
import java.io.File
import java.util.*

class NdkToolchainConfig(private val cppLibraryAndroid: CppLibraryAndroid) {

    companion object {
        private val logger = Logging.getLogger(NdkToolchainConfig::class.java)
        private fun OperatingSystem.androidLlvmToolchainPrefix(): String {
            return when {
                isMacOsX -> nativePrefix
                isLinux -> familyName
                isWindows -> familyName
                else -> throw RuntimeException("Unsupported operating system: ${this.name}")
            }
        }

        private const val sourcePropertiesFileName = "source.properties"
    }

    fun path(operatingSystem: OperatingSystem): List<File> {
        val llvmLocation = llvmToolchainLocation(operatingSystem)
        return listOf(File(llvmLocation, "bin"))
    }

    fun llvmToolchainLocation(operatingSystem: OperatingSystem) =
            File(cppLibraryAndroid.ndkDir, "toolchains/llvm/prebuilt/${operatingSystem.androidLlvmToolchainPrefix()}-x86_64")

    fun configure(toolChain: DefaultGccPlatformToolChain, androidInfo: AndroidInfo, cppLibraryAndroid: CppLibraryAndroid) {
        val ANDROID_NDK = this.cppLibraryAndroid.ndkDir
                ?: throw GradleException("Android NDK was not found in $localPropertiesFileName file specified as $ndkDirPropertyName.\n" +
                        "Also there's no $androidNdkHomeEnvironmentVariableName environment variable specified.\n" +
                        "Then it can be that, no Android SDK is found specified as $sdkDirPropertyName in $localPropertiesFileName.\n" +
                        "An attempt was made to find Android SDK defined as $androidSdkHomeEnvironmentVariableName environment variable.\n" +
                        "It also can be that Android NDK version specified in ${CppLibraryAndroid.NAME} was not downloaded in Android SDK\n" +
                        "ndk-bundle was considered too.")
        validateSourcePropertiesFileContent(ANDROID_NDK)
        val platformsMap = readPlatformsFile(ANDROID_NDK)
        platformsMap["NDK_MIN_PLATFORM_LEVEL"]?.let { androidInfo.minApi = it.toInt(radix = 10) }
        platformsMap["NDK_MAX_PLATFORM_LEVEL"]?.let { androidInfo.maxApi = it.toInt(radix = 10) }

        val ANDROID_ARM_MODE = cppLibraryAndroid.armMode.takeIf { it == "arm" } ?: "thumb"
        val ANDROID_CPP_FEATURES: List<String> = cppLibraryAndroid.cppFeatures.takeIf { it.isNotEmpty() }
                ?: listOf("rtti", "exceptions").takeIf { cppLibraryAndroid.forceCppFeatures }
                ?: listOf()

        val ANDROID_COMPILER_FLAGS = mutableListOf<String>()
        val ANDROID_COMPILER_FLAGS_CXX = mutableListOf<String>()
        val ANDROID_LINKER_FLAGS = mutableListOf<String>()
        val ANDROID_LINKER_FLAGS_EXE = mutableListOf<String>()
        val ANDROID_CXX_STANDARD_LIBRARIES = mutableListOf<String>()
        val CMAKE_SYSTEM_LIBRARY_PATH = mutableListOf<File>()
        val CMAKE_C_STANDARD_LIBRARIES_INIT = mutableListOf<String>()
        val CMAKE_CXX_STANDARD_LIBRARIES_INIT = mutableListOf<String>()

        ANDROID_LINKER_FLAGS.add("-Wl,--exclude-libs,libgcc.a")
        ANDROID_LINKER_FLAGS.add("-Wl,--exclude-libs,libatomic.a")

        val ANDROID_HOST_TAG = generateAndroidHostTag()

        val ANDROID_TOOLCHAIN_SUFFIX = ".exe".takeIf { OperatingSystem.current().isWindows } ?: ""
        val ANDROID_TOOLCHAIN_ROOT = File(ANDROID_NDK, "toolchains/llvm/prebuilt/${ANDROID_HOST_TAG}")
        val ANDROID_TOOLCHAIN_PREFIX = "bin/${androidInfo.toolchainName}-"

        val CMAKE_SYSROOT = File(ANDROID_TOOLCHAIN_ROOT, "sysroot")

        CMAKE_SYSTEM_LIBRARY_PATH.add(File(CMAKE_SYSROOT, "usr/lib/${androidInfo.toolchainName}/${androidInfo.api}"))

        val ANDROID_C_COMPILER = File(ANDROID_TOOLCHAIN_ROOT, "bin/clang${ANDROID_TOOLCHAIN_SUFFIX}")
        val ANDROID_CXX_COMPILER = File(ANDROID_TOOLCHAIN_ROOT, "bin/clang++${ANDROID_TOOLCHAIN_SUFFIX}")
        val ANDROID_ASM_COMPILER = File(ANDROID_TOOLCHAIN_ROOT, "bin/clang${ANDROID_TOOLCHAIN_SUFFIX}")
        val ANDROID_AR = File(ANDROID_TOOLCHAIN_ROOT, "${ANDROID_TOOLCHAIN_PREFIX}ar${ANDROID_TOOLCHAIN_SUFFIX}")

        ANDROID_COMPILER_FLAGS.addAll(listOf(
                "-g",
                "-DANDROID",
                "-fdata-sections",
                "-ffunction-sections",
                "-funwind-tables",
                "-fstack-protector-strong",
                "-no-canonical-prefixes"
        ))
        ANDROID_LINKER_FLAGS.addAll(listOf(
                "-Wl,--build-id",
                "-Wl,--warn-shared-textrel",
                "-Wl,--fatal-warnings",
                "-l${cppLibraryAndroid.stl}"
        ))
        ANDROID_LINKER_FLAGS_EXE.add("-Wl,--gc-sections")
        cppLibraryAndroid.stl.takeIf { it == "c++_static" }
                ?.let { ANDROID_LINKER_FLAGS.add("-static-libstdc++") }

        if ("x86" == androidInfo.arch && androidInfo.api < 24) {
            ANDROID_COMPILER_FLAGS.add("-mstackrealign")
        }
        ANDROID_COMPILER_FLAGS.add("-fno-addrsig")

        if (cppLibraryAndroid.stl.matches("^c\\+\\+_".toRegex()) && androidInfo.arch.matches("^armeabi".toRegex())) {
            ANDROID_LINKER_FLAGS.add("-Wl,--exclude-libs,libunwind.a")
        }

        CMAKE_C_STANDARD_LIBRARIES_INIT.addAll(listOf("-latomic", "-lm"))
        CMAKE_CXX_STANDARD_LIBRARIES_INIT.addAll(CMAKE_C_STANDARD_LIBRARIES_INIT)
        if (ANDROID_CXX_STANDARD_LIBRARIES.isNotEmpty()) {
            CMAKE_CXX_STANDARD_LIBRARIES_INIT.addAll(ANDROID_CXX_STANDARD_LIBRARIES)
        }

        ANDROID_CPP_FEATURES.filterNot { it.matches("^(rtti|exceptions)$".toRegex()) }
                .takeIf { it.isNotEmpty() }
                ?.let { throw GradleException("Invalid Android C++ features: ${it}.") }

        ANDROID_CPP_FEATURES.forEach { feature ->
            ANDROID_COMPILER_FLAGS_CXX.add("-f${feature}")
        }
        ANDROID_COMPILER_FLAGS_CXX.add("-fPIE")
        ANDROID_LINKER_FLAGS.add("-fPIE")
        ANDROID_LINKER_FLAGS.add("-pie")

        if (androidInfo.arch.matches("armeabi".toRegex())) {
            ANDROID_COMPILER_FLAGS.add("-march=armv7-a")
            if (ANDROID_ARM_MODE == "thumb") {
                ANDROID_COMPILER_FLAGS.add("-mthumb")
            } else if (ANDROID_ARM_MODE != "arm") {
                throw GradleException("Invalid Android ARM mode: ${ANDROID_ARM_MODE}.")
            }
            if (androidInfo.arch == "armeabi-v7a" && cppLibraryAndroid.isNeon) {
                ANDROID_COMPILER_FLAGS.add("-mfpu=neon")
            }
        }

        ANDROID_LINKER_FLAGS.add("-Qunused-arguments")
        ANDROID_COMPILER_FLAGS.add("-Wa,--noexecstack")
        ANDROID_LINKER_FLAGS.add("-Wl,-z,noexecstack")

        if (cppLibraryAndroid.disableFormatStringChecks) {
            ANDROID_COMPILER_FLAGS.add("-Wno-error=format-security")
        } else ANDROID_COMPILER_FLAGS.addAll(listOf("-Wformat", "-Werror=format-security"))

        val CMAKE_CXX_FLAGS = ANDROID_COMPILER_FLAGS + ANDROID_COMPILER_FLAGS_CXX
        val CMAKE_EXE_LINKER_FLAGS = ANDROID_LINKER_FLAGS + ANDROID_LINKER_FLAGS_EXE + listOf("-v")

        // variables which are not present in Android CMake toolchain
//        CMAKE_SYSTEM_LIBRARY_PATH.add(File(CMAKE_SYSROOT, "/usr/lib/${androidInfo.toolchainName}"))
        val ANDROID_SYSTEM_INCLUDES = listOf<File>(
//                File(CMAKE_SYSROOT, "usr/include/${androidInfo.toolchainName}"),
//                File(CMAKE_SYSROOT, "usr/include")
//                File(ANDROID_TOOLCHAIN_ROOT, "include/c++/4.9.x")
//                File(CMAKE_SYSROOT, "usr/include/c++/v1")
        )
        val ANDROID_SYMBOL_EXTRACTOR = File(ANDROID_TOOLCHAIN_ROOT, "${ANDROID_TOOLCHAIN_PREFIX}objcopy${ANDROID_TOOLCHAIN_SUFFIX}")
        val ANDROID_STRIPPER = File(ANDROID_TOOLCHAIN_ROOT, "${ANDROID_TOOLCHAIN_PREFIX}strip${ANDROID_TOOLCHAIN_SUFFIX}")
        val ANDROID_TARGET_FLAGS = listOf("--target=${androidInfo.llvmTriple}")
        val ANDROID_SYSROOT_FLAGS = listOf("-isysroot", CMAKE_SYSROOT.path)
        val ANDROID_SYSTEM_LIBRARY_PATH_FLAGS = CMAKE_SYSTEM_LIBRARY_PATH.flatMap { listOf("-L", it.path) }
        val ANDROID_SYSTEM_INCLUDES_FLAGS = ANDROID_SYSTEM_INCLUDES.flatMap { listOf("-isystem", it.path) }
        val ANDROID_ADDITIONAL_FLAGS = ANDROID_TARGET_FLAGS + ANDROID_SYSROOT_FLAGS + ANDROID_SYSTEM_INCLUDES_FLAGS

        mapOf(
                (ToolType.C_COMPILER to ANDROID_C_COMPILER) to (ANDROID_COMPILER_FLAGS + ANDROID_ADDITIONAL_FLAGS),
                (ToolType.CPP_COMPILER to ANDROID_CXX_COMPILER) to (CMAKE_CXX_FLAGS + ANDROID_ADDITIONAL_FLAGS),
                (ToolType.LINKER to ANDROID_CXX_COMPILER) to (CMAKE_EXE_LINKER_FLAGS + ANDROID_SYSTEM_LIBRARY_PATH_FLAGS + ANDROID_ADDITIONAL_FLAGS),
                (ToolType.STATIC_LIB_ARCHIVER to ANDROID_AR) to emptyList(),
                (ToolType.ASSEMBLER to ANDROID_ASM_COMPILER) to (ANDROID_COMPILER_FLAGS + ANDROID_ADDITIONAL_FLAGS),
                (ToolType.SYMBOL_EXTRACTOR to ANDROID_SYMBOL_EXTRACTOR) to emptyList(),
                (ToolType.STRIPPER to ANDROID_STRIPPER) to emptyList()
        ).map { (configuration, arguments) ->
            configuration.also {
                it.second.takeUnless { file -> file.canExecute() }?.setExecutable(true, true)
            }.let { (type, file) ->
                DefaultGccCommandLineToolConfiguration(type, file.path) to arguments
            }
        }.onEach { (toolConfiguration, arguments) ->
            toolConfiguration.also {
                toolChain.add(it)
            }.withArguments {
                addAll(arguments)
            }
        }
    }

    private fun generateAndroidHostTag(): String = with(OperatingSystem.current()) {
        when {
            isLinux -> "linux-x86_64"
            isWindows -> "windows-x86_64"
            isMacOsX -> "darwin-x86_64"
            else -> throw GradleException("Unsupported host OS: $name")
        }
    }


    private fun readPlatformsFile(androidNdk: File): Map<String, String> {
        return File(androidNdk, "build/cmake/platforms.cmake")
                .readLines()
                .map {
                    it.substringAfter('(')
                            .substringBefore(')')
                }.map {
                    it.split(" ").let { split -> split[0] to split[1].replace("\"", "") }
                }.toMap()
    }

    private fun validateSourcePropertiesFileContent(androidNdk: File) {
        val (description, revision) = File(androidNdk, sourcePropertiesFileName)
                .bufferedReader()
                .let { reader ->
                    Properties().also { it.load(reader) }.also { reader.close() }
                }.let { it.getProperty("Pkg.Desc") to it.getProperty("Pkg.Revision") }
        description.takeUnless { it == "Android NDK" }?.let {
            throw GradleException("Failed to parse Android NDK revision. Description is not equals \"Android NDK\"")
        }
        revision.takeUnless { it.matches("^([0-9]+)\\.([0-9]+)\\.([0-9]+)(-beta([0-9]+))?".toRegex()) }?.let {
            throw GradleException("Failed to parse Android NDK revision. Detected revision: $it")
        }
        logger.info("Found Android NDK revision $revision at ${androidNdk.path}")
    }

}
