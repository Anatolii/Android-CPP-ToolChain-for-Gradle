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
        val ANDROID_ABI = androidInfo.arch
        val ANDROID_ARM_NEON = cppLibraryAndroid.isNeon
        var ANDROID_PLATFORM_LEVEL = androidInfo.api
        val ANDROID_TOOLCHAIN = "clang"
        val platformsMap = readPlatformsFile(ANDROID_NDK)
        platformsMap.getValue("NDK_MIN_PLATFORM_LEVEL").toInt().takeIf { it > ANDROID_PLATFORM_LEVEL }
                ?.let { ANDROID_PLATFORM_LEVEL = it }
        ANDROID_ABI.takeIf { ANDROID_PLATFORM_LEVEL < 21 && it.matches("64(-v8a)?$".toRegex()) }
                ?.let { ANDROID_PLATFORM_LEVEL = 21 }
        platformsMap.getValue("NDK_MAX_PLATFORM_LEVEL").toInt().takeIf { it < ANDROID_PLATFORM_LEVEL }
                ?.let { throw GradleException("$ANDROID_PLATFORM_LEVEL is above the maximum supported version ${it}. Choose a supported API level.") }

        val ANDROID_PLATFORM = "android-${ANDROID_PLATFORM_LEVEL}"
        val ANDROID_STL = cppLibraryAndroid.stl
        val ANDROID_PIE = true
        val ANDROID_ARM_MODE = cppLibraryAndroid.armMode.takeIf { it == "arm" } ?: "thumb"
        val ANDROID_SYSROOT_ABI = androidInfo.sysrootAbi
        val ANDROID_TOOLCHAIN_NAME = androidInfo.toolchainName
        val ANDROID_LLVM_TRIPLE = androidInfo.llvmTriple + ANDROID_PLATFORM_LEVEL
        val ANDROID_STL_FORCE_FEATURES = cppLibraryAndroid.forceCppFeatures
        val ANDROID_CPP_FEATURES: List<String> = cppLibraryAndroid.cppFeatures.takeIf { it.isNotEmpty() }
                ?: listOf("rtti", "exceptions").takeIf { ANDROID_STL_FORCE_FEATURES }
                ?: listOf()

        val ANDROID_COMPILER_FLAGS = mutableListOf<String>()
        val ANDROID_COMPILER_FLAGS_CXX = mutableListOf<String>()
        val ANDROID_COMPILER_FLAGS_DEBUG = mutableListOf<String>()
        val ANDROID_COMPILER_FLAGS_RELEASE = mutableListOf<String>()
        val ANDROID_LINKER_FLAGS = mutableListOf<String>()
        val ANDROID_LINKER_FLAGS_EXE = mutableListOf<String>()
        val ANDROID_CXX_STANDARD_LIBRARIES = mutableListOf<String>()
        val CMAKE_SYSTEM_LIBRARY_PATH = mutableListOf<File>()
        val CMAKE_C_STANDARD_LIBRARIES_INIT = mutableListOf<String>()
        val CMAKE_CXX_STANDARD_LIBRARIES_INIT = mutableListOf<String>()

        ANDROID_LINKER_FLAGS.add("-Wl,--exclude-libs,libgcc.a")
        ANDROID_LINKER_FLAGS.add("-Wl,--exclude-libs,libatomic.a")

        ANDROID_COMPILER_FLAGS_CXX.add("-stdlib=libc++")
        ANDROID_CXX_STANDARD_LIBRARIES.takeUnless { ANDROID_CPP_FEATURES.isEmpty() }
                ?.also { it.add("-lc++abi") }
                ?.takeIf { ANDROID_PLATFORM_LEVEL < 21 }
                ?.also { it.add("-landroid_support") }
        when (ANDROID_STL.trim()) {
            "c++_static" -> ANDROID_LINKER_FLAGS.add("-static-libc++")
            "c++_shared", "" -> {
                ANDROID_COMPILER_FLAGS_CXX.add("-nostdinc++")
                ANDROID_LINKER_FLAGS.add("-nostdlib++")
            }
            else -> throw GradleException("Invalid Android STL: ${ANDROID_STL}.")
        }

        val ANDROID_HOST_TAG = generateAndroidHostTag()

        val ANDROID_TOOLCHAIN_SUFFIX = ".exe".takeIf { OperatingSystem.current().isWindows } ?: ""
        val ANDROID_TOOLCHAIN_ROOT = File(ANDROID_NDK, "toolchains/llvm/prebuilt/${ANDROID_HOST_TAG}")
        val ANDROID_TOOLCHAIN_PREFIX = "bin/${ANDROID_TOOLCHAIN_NAME}-"

        val CMAKE_SYSROOT = File(ANDROID_TOOLCHAIN_ROOT, "sysroot")
        val CMAKE_LIBRARY_ARCHITECTURE = ANDROID_TOOLCHAIN_NAME
        CMAKE_SYSTEM_LIBRARY_PATH.add(File(CMAKE_SYSROOT, "usr/lib/${ANDROID_TOOLCHAIN_NAME}/${ANDROID_PLATFORM_LEVEL}"))
        val ANDROID_HOST_PREBUILTS = File(ANDROID_NDK, "prebuilt/${ANDROID_HOST_TAG}")

        val ANDROID_C_COMPILER = File(ANDROID_TOOLCHAIN_ROOT, "bin/clang${ANDROID_TOOLCHAIN_SUFFIX}")
        val ANDROID_CXX_COMPILER = File(ANDROID_TOOLCHAIN_ROOT, "bin/clang++${ANDROID_TOOLCHAIN_SUFFIX}")
        val ANDROID_ASM_COMPILER = File(ANDROID_TOOLCHAIN_ROOT, "bin/clang${ANDROID_TOOLCHAIN_SUFFIX}")
        val CMAKE_C_COMPILER_TARGET = "${ANDROID_LLVM_TRIPLE}"
        val CMAKE_CXX_COMPILER_TARGET = "${ANDROID_LLVM_TRIPLE}"
        val CMAKE_ASM_COMPILER_TARGET = "${ANDROID_LLVM_TRIPLE}"
        val CMAKE_C_COMPILER_EXTERNAL_TOOLCHAIN = "${ANDROID_TOOLCHAIN_ROOT}"
        val CMAKE_CXX_COMPILER_EXTERNAL_TOOLCHAIN = "${ANDROID_TOOLCHAIN_ROOT}"
        val CMAKE_ASM_COMPILER_EXTERNAL_TOOLCHAIN = "${ANDROID_TOOLCHAIN_ROOT}"
        val ANDROID_AR = File(ANDROID_TOOLCHAIN_ROOT, "${ANDROID_TOOLCHAIN_PREFIX}ar${ANDROID_TOOLCHAIN_SUFFIX}")
        val ANDROID_RANLIB = File(ANDROID_TOOLCHAIN_ROOT, "${ANDROID_TOOLCHAIN_PREFIX}ranlib${ANDROID_TOOLCHAIN_SUFFIX}")

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
                "-Wl,--fatal-warnings"
        ))
        ANDROID_LINKER_FLAGS_EXE.add("-Wl,--gc-sections")
        ANDROID_COMPILER_FLAGS_DEBUG.add("-O0")
        if (ANDROID_ABI.matches("^armeabi".toRegex()) && ANDROID_ARM_MODE == "thumb") {
            ANDROID_COMPILER_FLAGS_RELEASE.add("-Oz")
        } else ANDROID_COMPILER_FLAGS_RELEASE.add(("-O2"))
        ANDROID_COMPILER_FLAGS_RELEASE.add("-DNDEBUG")
        ANDROID_COMPILER_FLAGS_DEBUG.add("-fno-limit-debug-info")

        if ("x86" == ANDROID_ABI && ANDROID_PLATFORM_LEVEL < 24) {
            ANDROID_COMPILER_FLAGS.add("-mstackrealign")
        }
        ANDROID_COMPILER_FLAGS.add("-fno-addrsig")

        if (ANDROID_STL.matches("^c\\+\\+_".toRegex()) && ANDROID_ABI.matches("^armeabi".toRegex())) {
            ANDROID_LINKER_FLAGS.add("-Wl,--exclude-libs,libunwind.a")
        }

        CMAKE_C_STANDARD_LIBRARIES_INIT.addAll(listOf("-latomic", "-lm"))
        CMAKE_CXX_STANDARD_LIBRARIES_INIT.addAll(CMAKE_C_STANDARD_LIBRARIES_INIT)
        if (ANDROID_CXX_STANDARD_LIBRARIES.isNotEmpty()) {
            CMAKE_CXX_STANDARD_LIBRARIES_INIT.addAll(ANDROID_CXX_STANDARD_LIBRARIES)
        }

        val CMAKE_POSITION_INDEPENDENT_CODE = ANDROID_PIE

        ANDROID_CPP_FEATURES.filterNot { it.matches("^(rtti|exceptions)$".toRegex()) }
                .takeIf { it.isNotEmpty() }
                ?.let { throw GradleException("Invalid Android C++ features: ${it}.") }

        ANDROID_CPP_FEATURES.forEach { feature ->
            ANDROID_COMPILER_FLAGS_CXX.add("-f${feature}")
        }
        if (ANDROID_ABI.matches("armeabi".toRegex())) {
            ANDROID_COMPILER_FLAGS.add("-march=armv7-a")
            if (ANDROID_ARM_MODE == "thumb") {
                ANDROID_COMPILER_FLAGS.add("-mthumb")
            } else if (ANDROID_ARM_MODE != "arm") {
                throw GradleException("Invalid Android ARM mode: ${ANDROID_ARM_MODE}.")
            }
            if (ANDROID_ABI == "armeabi-v7a" && ANDROID_ARM_NEON) {
                ANDROID_COMPILER_FLAGS.add("-mfpu=neon")
            }
        }

        ANDROID_LINKER_FLAGS.add("-Qunused-arguments")
        ANDROID_COMPILER_FLAGS.add("-Wa,--noexecstack")
        ANDROID_LINKER_FLAGS.add("-Wl,-z,noexecstack")

        if (cppLibraryAndroid.disableFormatStringChecks) {
            ANDROID_COMPILER_FLAGS.add("-Wno-error=format-security")
        } else ANDROID_COMPILER_FLAGS.addAll(listOf("-Wformat", "-Werror=format-security"))

        val CMAKE_C_COMPILER = ANDROID_C_COMPILER
        val CMAKE_CXX_COMPILER = ANDROID_CXX_COMPILER
        val CMAKE_AR = ANDROID_AR
        val CMAKE_RANLIB = ANDROID_RANLIB

        if (ANDROID_ABI == "x86" || ANDROID_ABI == "x86_64") {
            val CMAKE_ASM_NASM_COMPILER = "${ANDROID_TOOLCHAIN_ROOT}/bin/yasm${ANDROID_TOOLCHAIN_SUFFIX}"
            val CMAKE_ASM_NASM_COMPILER_ARG1 = "-DELF"
        }

        val CMAKE_C_FLAGS = ANDROID_COMPILER_FLAGS
        val CMAKE_CXX_FLAGS = ANDROID_COMPILER_FLAGS + ANDROID_COMPILER_FLAGS_CXX
        val CMAKE_ASM_FLAGS = ANDROID_COMPILER_FLAGS
        val CMAKE_C_FLAGS_DEBUG = ANDROID_COMPILER_FLAGS_DEBUG
        val CMAKE_CXX_FLAGS_DEBUG = ANDROID_COMPILER_FLAGS_DEBUG
        val CMAKE_ASM_FLAGS_DEBUG = ANDROID_COMPILER_FLAGS_DEBUG
        val CMAKE_C_FLAGS_RELEASE = ANDROID_COMPILER_FLAGS_RELEASE
        val CMAKE_CXX_FLAGS_RELEASE = ANDROID_COMPILER_FLAGS_RELEASE
        val CMAKE_ASM_FLAGS_RELEASE = ANDROID_COMPILER_FLAGS_RELEASE
        val CMAKE_SHARED_LINKER_FLAGS = ANDROID_LINKER_FLAGS + listOf("-v")
        val CMAKE_MODULE_LINKER_FLAGS = ANDROID_LINKER_FLAGS + listOf("-v")
        val CMAKE_EXE_LINKER_FLAGS = ANDROID_LINKER_FLAGS + ANDROID_LINKER_FLAGS_EXE + listOf("-v")

        // variables which are not present in Android CMake toolchain
        CMAKE_SYSTEM_LIBRARY_PATH.add(File(CMAKE_SYSROOT, "/usr/lib/${androidInfo.toolchainName}"))
        val ANDROID_SYSTEM_INCLUDES = listOf<File>(
                File(CMAKE_SYSROOT, "usr/include/${androidInfo.toolchainName}"),
                File(CMAKE_SYSROOT, "usr/include"),
                File(CMAKE_SYSROOT, "usr/include/c++/v1"),
                File(ANDROID_TOOLCHAIN_ROOT, "include/c++/4.9.x")
        )
        val ANDROID_SYMBOL_EXTRACTOR = File(ANDROID_TOOLCHAIN_ROOT, "${ANDROID_TOOLCHAIN_PREFIX}objcopy${ANDROID_TOOLCHAIN_SUFFIX}")
        val ANDROID_STRIPPER = File(ANDROID_TOOLCHAIN_ROOT, "${ANDROID_TOOLCHAIN_PREFIX}strip${ANDROID_TOOLCHAIN_SUFFIX}")
        val ANDROID_TARGET_FLAGS = listOf("--target=${ANDROID_LLVM_TRIPLE}", "-fno-addrsig")
        val ANDROID_SYSROOT_FLAGS = listOf("-isysroot", CMAKE_SYSROOT.path)
        val ANDROID_SYSTEM_LIBRARY_PATH_FLAGS = CMAKE_SYSTEM_LIBRARY_PATH.flatMap { listOf("-L", it.path) }
        val ANDROID_SYSTEM_INCLUDES_FLAGS = ANDROID_SYSTEM_INCLUDES.flatMap { listOf("-isystem", it.path) }
        val ANDROID_ADDITIONAL_FLAGS = ANDROID_TARGET_FLAGS + ANDROID_SYSROOT_FLAGS + ANDROID_SYSTEM_LIBRARY_PATH_FLAGS + ANDROID_SYSTEM_INCLUDES_FLAGS

        mapOf(
                (ToolType.C_COMPILER to CMAKE_C_COMPILER) to (CMAKE_C_FLAGS + ANDROID_ADDITIONAL_FLAGS),
                (ToolType.CPP_COMPILER to CMAKE_CXX_COMPILER) to (CMAKE_CXX_FLAGS + ANDROID_ADDITIONAL_FLAGS),
                (ToolType.LINKER to CMAKE_CXX_COMPILER) to (CMAKE_EXE_LINKER_FLAGS + ANDROID_ADDITIONAL_FLAGS),
                (ToolType.STATIC_LIB_ARCHIVER to CMAKE_AR) to emptyList(),
                (ToolType.ASSEMBLER to ANDROID_ASM_COMPILER) to (ANDROID_ADDITIONAL_FLAGS),
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
