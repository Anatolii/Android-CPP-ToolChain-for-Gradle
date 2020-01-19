package dev.anatolii.gradle.cpp.android.toolchain

import dev.anatolii.gradle.cpp.android.AndroidInfo
import dev.anatolii.gradle.cpp.android.CppLibraryAndroid
import dev.anatolii.gradle.cpp.android.ndk.NdkToolchainConfig
import dev.anatolii.gradle.cpp.android.ndk.androidNdkHomeEnvironmentVariableName
import dev.anatolii.gradle.cpp.android.ndk.androidSdkHomeEnvironmentVariableName
import dev.anatolii.gradle.cpp.android.ndk.localPropertiesFileName
import dev.anatolii.gradle.cpp.android.ndk.ndkDirPropertyName
import dev.anatolii.gradle.cpp.android.ndk.sdkDirPropertyName
import org.gradle.api.GradleException
import org.gradle.internal.os.OperatingSystem
import org.gradle.nativeplatform.toolchain.internal.ToolType
import org.gradle.nativeplatform.toolchain.internal.gcc.DefaultGccPlatformToolChain
import org.gradle.nativeplatform.toolchain.internal.tools.DefaultGccCommandLineToolConfiguration
import java.io.File

/**
 * Represents configuration which is done in NDK/build/cmake/android.toolchain.cmake file.
 */
internal fun DefaultGccPlatformToolChain.configure(toolchainConfig: NdkToolchainConfig, androidInfo: AndroidInfo) {
    val androidNdk = toolchainConfig.cppLibraryAndroid.ndkDir
            ?: throw GradleException("Android NDK was not found in $localPropertiesFileName file specified as $ndkDirPropertyName.\n" +
                    "Also there's no $androidNdkHomeEnvironmentVariableName environment variable specified.\n" +
                    "Then it can be that, no Android SDK is found specified as $sdkDirPropertyName in $localPropertiesFileName.\n" +
                    "An attempt was made to find Android SDK defined as $androidSdkHomeEnvironmentVariableName environment variable.\n" +
                    "It also can be that Android NDK version specified in ${CppLibraryAndroid.NAME} was not downloaded in Android SDK\n" +
                    "ndk-bundle was considered too.")
    NdkToolchainConfig.validateSourcePropertiesFileContent(androidNdk)
    val platformsMap = NdkToolchainConfig.readPlatformsFile(androidNdk)
    platformsMap["NDK_MIN_PLATFORM_LEVEL"]?.let { androidInfo.minApi = it.toInt(radix = 10) }
    platformsMap["NDK_MAX_PLATFORM_LEVEL"]?.let { androidInfo.maxApi = it.toInt(radix = 10) }

    val androidArmMode = toolchainConfig.cppLibraryAndroid.armMode.takeIf { it == "arm" } ?: "thumb"
    val androidCppFeatures: List<String> = toolchainConfig.cppLibraryAndroid.cppFeatures.takeIf { it.isNotEmpty() }
            ?: listOf("rtti", "exceptions").takeIf { toolchainConfig.cppLibraryAndroid.forceCppFeatures }
            ?: listOf()

    val androidCompilerFlags = mutableListOf<String>()
    val androidCompilerFlagsCxx = mutableListOf<String>()
    val androidLinkerFlags = mutableListOf<String>()
    val androidLinkerFlagsExe = mutableListOf<String>()
    val androidCxxStandardLibraries = mutableListOf<String>()
    val cmakeSystemLibraryPath = mutableListOf<File>()
    val cmakeCStandardLibrariesInit = mutableListOf<String>()
    val cmakeCxxStandardLibrariesInit = mutableListOf<String>()

    androidLinkerFlags.add("-Wl,--exclude-libs,libgcc.a")
    androidLinkerFlags.add("-Wl,--exclude-libs,libatomic.a")

    val androidHostTag = NdkToolchainConfig.generateAndroidHostTag()

    val androidToolchainSuffix = ".exe".takeIf { OperatingSystem.current().isWindows } ?: ""
    val androidToolchainRoot = File(androidNdk, "toolchains/llvm/prebuilt/${androidHostTag}")
    val androidToolchainPrefix = "bin/${androidInfo.toolchainName}-"

    val cmakeSysroot = File(androidToolchainRoot, "sysroot")

    cmakeSystemLibraryPath.add(File(cmakeSysroot, "usr/lib/${androidInfo.toolchainName}/${androidInfo.api}"))

    val androidCCompiler = File(androidToolchainRoot, "bin/clang${androidToolchainSuffix}")
    val androidCxxCompiler = File(androidToolchainRoot, "bin/clang++${androidToolchainSuffix}")
    val androidAsmCompiler = File(androidToolchainRoot, "bin/clang${androidToolchainSuffix}")
    val androidAr = File(androidToolchainRoot, "${androidToolchainPrefix}ar${androidToolchainSuffix}")

    androidCompilerFlags.addAll(listOf(
            "-g",
            "-DANDROID",
            "-fdata-sections",
            "-ffunction-sections",
            "-funwind-tables",
            "-fstack-protector-strong",
            "-no-canonical-prefixes"
    ))
    androidLinkerFlags.addAll(listOf(
            "-Wl,--build-id",
            "-Wl,--warn-shared-textrel",
            "-Wl,--fatal-warnings",
            "-l${toolchainConfig.cppLibraryAndroid.stl}"
    ))
    androidLinkerFlagsExe.add("-Wl,--gc-sections")
    toolchainConfig.cppLibraryAndroid.stl.takeIf { it == "c++_static" }
            ?.let { androidLinkerFlags.add("-static-libstdc++") }

    if ("x86" == androidInfo.arch && androidInfo.api < 24) {
        androidCompilerFlags.add("-mstackrealign")
    }
    androidCompilerFlags.add("-fno-addrsig")

    if (toolchainConfig.cppLibraryAndroid.stl.matches("^c\\+\\+_".toRegex()) && androidInfo.arch.matches("^armeabi".toRegex())) {
        androidLinkerFlags.add("-Wl,--exclude-libs,libunwind.a")
    }

    cmakeCStandardLibrariesInit.addAll(listOf("-latomic", "-lm"))
    cmakeCxxStandardLibrariesInit.addAll(cmakeCStandardLibrariesInit)
    if (androidCxxStandardLibraries.isNotEmpty()) {
        cmakeCxxStandardLibrariesInit.addAll(androidCxxStandardLibraries)
    }

    androidCppFeatures.filterNot { it.matches("^(rtti|exceptions)$".toRegex()) }
            .takeIf { it.isNotEmpty() }
            ?.let { throw GradleException("Invalid Android C++ features: ${it}.") }

    androidCppFeatures.forEach { feature ->
        androidCompilerFlagsCxx.add("-f${feature}")
    }
    androidCompilerFlagsCxx.add("-fPIE")
    androidLinkerFlags.add("-fPIE")
    androidLinkerFlags.add("-pie")

    if (androidInfo.arch.matches("armeabi".toRegex())) {
        androidCompilerFlags.add("-march=armv7-a")
        if (androidArmMode == "thumb") {
            androidCompilerFlags.add("-mthumb")
        } else if (androidArmMode != "arm") {
            throw GradleException("Invalid Android ARM mode: ${androidArmMode}.")
        }
        if (androidInfo.arch == "armeabi-v7a" && toolchainConfig.cppLibraryAndroid.isNeon) {
            androidCompilerFlags.add("-mfpu=neon")
        }
    }

    androidLinkerFlags.add("-Qunused-arguments")
    androidCompilerFlags.add("-Wa,--noexecstack")
    androidLinkerFlags.add("-Wl,-z,noexecstack")

    if (toolchainConfig.cppLibraryAndroid.disableFormatStringChecks) {
        androidCompilerFlags.add("-Wno-error=format-security")
    } else androidCompilerFlags.addAll(listOf("-Wformat", "-Werror=format-security"))

    val cmakeCxxFlags = androidCompilerFlags + androidCompilerFlagsCxx
    val cmakeExeLinkerFlags = androidLinkerFlags + androidLinkerFlagsExe + listOf("-v")

    val androidSymbolExtractor = File(androidToolchainRoot, "${androidToolchainPrefix}objcopy${androidToolchainSuffix}")
    val androidStripper = File(androidToolchainRoot, "${androidToolchainPrefix}strip${androidToolchainSuffix}")
    val androidTargetFlags = listOf("--target=${androidInfo.llvmTriple}")
    val androidSysrootFlags = listOf("-isysroot", cmakeSysroot.path)
    val androidSystemLibraryPathFlags = cmakeSystemLibraryPath.flatMap { listOf("-L", it.path) }
    val androidAdditionalFlags = androidTargetFlags + androidSysrootFlags

    mapOf(
            (ToolType.C_COMPILER to androidCCompiler) to (androidCompilerFlags + androidAdditionalFlags),
            (ToolType.CPP_COMPILER to androidCxxCompiler) to (cmakeCxxFlags + androidAdditionalFlags),
            (ToolType.LINKER to androidCxxCompiler) to (cmakeExeLinkerFlags + androidSystemLibraryPathFlags + androidAdditionalFlags),
            (ToolType.STATIC_LIB_ARCHIVER to androidAr) to emptyList(),
            (ToolType.ASSEMBLER to androidAsmCompiler) to (androidCompilerFlags + androidAdditionalFlags),
            (ToolType.SYMBOL_EXTRACTOR to androidSymbolExtractor) to emptyList(),
            (ToolType.STRIPPER to androidStripper) to emptyList()
    ).onEach { (configuration, _) ->
        configuration.second.takeUnless { file -> file.canExecute() }?.setExecutable(true, true)
    }.map { (configuration, arguments) ->
        DefaultGccCommandLineToolConfiguration(configuration.first, configuration.second.path)
                .also { it.withArguments { addAll(arguments) } }
    }.onEach { toolConfiguration ->
        add(toolConfiguration)
    }
}
