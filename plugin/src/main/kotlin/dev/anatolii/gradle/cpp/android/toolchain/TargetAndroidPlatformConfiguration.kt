package dev.anatolii.gradle.cpp.android.toolchain

import org.gradle.nativeplatform.platform.internal.NativePlatformInternal

interface TargetAndroidPlatformConfiguration {
    /**
     * Returns whether a platform is supported or not.
     */
    fun supportsPlatform(targetPlatform: NativePlatformInternal): Boolean
}