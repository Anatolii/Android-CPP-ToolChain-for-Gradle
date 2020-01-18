package dev.anatolii.gradle.cpp.android

import org.gradle.api.GradleException

data class AndroidInfo(private val apiInternal: Int = defaultMinApi, val arch: String) {

    var minApi: Int = defaultMinApi
        get() {
            return when (arch) {
                armv7 -> defaultMinApi
                armv8 -> defaultMinApi64Bit
                x86 -> defaultMinApi
                x86_64 -> defaultMinApi64Bit
                else -> defaultMinApi
            }.let { default -> field.takeIf { it >= default } ?: default }
        }
    var maxApi: Int = Int.MAX_VALUE

    val api: Int
        get() = (apiInternal.takeIf { it >= minApi } ?: minApi)
                .takeIf { it <= maxApi }
                ?: throw Exception("Unsupported API level. Expected: [ ${minApi}..${maxApi} ] but was: $apiInternal")

    val sysrootAbi: String by lazy {
        when (arch) {
            armv7 -> "arm"
            armv8 -> "arm64"
            x86 -> x86
            x86_64 -> x86_64
            else -> throw GradleException("Invalid Android ABI: $arch")
        }
    }

    val toolchainName: String by lazy {
        when (arch) {
            armv7 -> "arm-linux-androideabi"
            armv8 -> "aarch64-linux-android"
            x86 -> "i686-linux-android"
            x86_64 -> "x86_64-linux-android"
            else -> throw GradleException("Invalid Android ABI: $arch")
        }
    }

    val llvmTriple: String by lazy {
        toolchainName + api
//        when (arch) {
//            armv7 -> "armv7-none-linux-androideabi"
//            armv8 -> "aarch64-none-linux-android"
//            x86 -> "i686-none-linux-android"
//            x86_64 -> "x86_64-none-linux-android"
//            else -> throw GradleException("Invalid Android ABI: $arch")
//        } + api
    }

    companion object {
        const val armv7 = "armeabi-v7a"
        const val armv8 = "arm64-v8a"
        const val x86 = "x86"
        const val x86_64 = "x86_64"
        const val platformPrefix = "android"
        const val platformNameSeparator = "_"
        const val defaultMinApi = 16
        const val defaultMinApi64Bit = 21
        private const val platformNameSeparatorSecondary = ":"
        private const val splitNameSize = 3

        fun fromPlatformName(platformName: String): AndroidInfo? = platformName.split(
                platformNameSeparator, platformNameSeparatorSecondary, ignoreCase = true, limit = splitNameSize
        ).takeIf {
            it.size == splitNameSize
                    && it[0] == platformPrefix
                    && it[1].all { c -> c.isDigit() }
                    && listOf(armv7, armv8, x86, x86_64).contains(it[2])
        }?.let { AndroidInfo(it[1].toInt(), it[2]) }
    }

    val platformName: String by lazy { listOf(platformPrefix, api, arch).joinToString(separator = platformNameSeparator) }

    val toolsPrefix: String by lazy {
        ("aarch64".takeIf { arch == armv8 } ?: "arm".takeIf { arch == armv7 }
        ?: "i686".takeIf { arch == x86 } ?: x86_64) +
                "-linux-android" + ("eabi".takeIf { arch == armv7 } ?: "")
    }

    val targetPrefix: String by lazy {
        ("aarch64".takeIf { arch == armv8 } ?: "armv7a".takeIf { arch == armv7 }
        ?: "i686".takeIf { arch == x86 } ?: x86_64) +
                "-linux-android" + ("eabi".takeIf { arch == armv7 } ?: "")

    }
}