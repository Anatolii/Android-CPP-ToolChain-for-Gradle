# Gradle cross-platform cpp build plugin

## Important information

This plugin is work in progress, use it for evaluation purposes and on your own risk.
The plugin relies on ordering of applied configurations in original [cpp-library](https://docs.gradle.org/current/userguide/cpp_library_plugin.html) or [cpp-application](https://docs.gradle.org/current/userguide/cpp_application_plugin.html). So when Gradle team will change something in there - plugin in this repository might stop working as well.

## Description

[![Build](https://github.com/Anatolii/gradle-cpp-cross-platform/workflows/Gradle%20build/badge.svg)](https://github.com/Anatolii/gradle-cpp-cross-platform/actions)


Gradle plugin which enables cross-platform compilation of CPP code using new plugins for native compilation.

This repository contains code for Android toolchain as example.

The plugin is built on top of [new C++ plugins](https://blog.gradle.org/update-on-the-new-cpp-plugins).

## Available plugins IDs

- dev.anatolii.cpp.crossplatform.library
- dev.anatolii.cpp.crossplatform.application
- dev.anatolii.cpp.android.library

## Usage example

```kotlin
import dev.anatolii.gradle.cpp.android.AndroidInfo
// ...
plugins {
    id("dev.anatolii.cpp.android.library")
}
// ...
library {
    // In order to set up Android target with API 28 and ARM V8 architecture use following line, where `arch` can be one of: armv7, armv8, x86, x86_64
    targetMachines.add(machines.os("Android").architecture(AndroidInfo(api = 28, arch = AndroidInfo.armv8).platformName))
    // Alternatively you can set the target architecture as string
    targetMachines.add(machines.os("Android").architecture("android28_x86"))
    targetMachines.add(machines.linux.x86_64)

    linkage.add(Linkage.SHARED)
    linkage.add(Linkage.STATIC)
}
```
