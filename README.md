# Gradle cross-platform cpp build plugin

## Important information

This plugin is work in progress, use it for evaluation purposes and on your own risk.

## Description

[![Build](https://github.com/Anatolii/gradle-cpp-cross-platform/workflows/Gradle%20build/badge.svg)](https://github.com/Anatolii/gradle-cpp-cross-platform/actions)

Gradle plugin which enables cross-platform compilation of CPP code using new plugins for native compilation.

This repository contains code for Android toolchain as example.

The plugin is built on top of [new C++ plugins](https://blog.gradle.org/update-on-the-new-cpp-plugins).

## Plugin ID

```text
dev.anatolii.cpp.android.clang
```

## Usage example

```kotlin
import dev.anatolii.gradle.cpp.android.AndroidInfo
// ...
plugins {
    id("cpp-library")
    id("dev.anatolii.cpp.android.clang")
}
// ...
library {
    // In order to set up Android target with API 28 and ARM V8 architecture use following line, where `arch` can be one of: armv7, armv8, x86, x86_64
    targetMachines.add(machines.linux.architecture(AndroidInfo(api = 28, arch = AndroidInfo.armv8).platformName))
    // Alternatively you can set the target architecture as string
    targetMachines.add(machines.linux.architecture("android28_x86"))
    // Pre-defined architectures will be configured by "cpp-library" plugin as usually.
    targetMachines.add(machines.linux.x86_64)

    linkage.add(Linkage.SHARED)
    linkage.add(Linkage.STATIC)
}
```

**NOTE**: Binaries published from Linux host will not be re-used by MacOS or Windows hosts due to current Gradle implementation for binary packages distribution. 