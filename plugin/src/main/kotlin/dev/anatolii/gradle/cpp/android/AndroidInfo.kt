package dev.anatolii.gradle.cpp.android

data class AndroidInfo(val api: Int, val arch: String) {

    companion object {
        const val armv7 = "armv7"
        const val armv8 = "armv8"
        const val x86 = "x86"
        const val x86_64 = "x86_64"
    }

    val platformName = "android${api}_$arch"

    val targetPrefix = ("aarch64".takeIf { arch == armv8 } ?: "armv7a".takeIf { arch == armv7 }
    ?: "i686".takeIf { arch == x86 } ?: x86_64) +
            "-linux-android" + ("eabi".takeIf { arch == armv7 } ?: "")

    val toolsPrefix = ("aarch64".takeIf { arch == armv8 } ?: "arm".takeIf { arch == armv7 }
    ?: "i686".takeIf { arch == x86 } ?: x86_64) +
            "-linux-android" + ("eabi".takeIf { arch == armv7 } ?: "")

}