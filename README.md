# Android CPP ToolChain plugin for Gradle 

## Important information

This plugin is work in progress, use it for evaluation purposes and on your own risk.
That is why the plugin is not published on Gradle plugins repository yet.

## Description

[![Build](https://github.com/Anatolii/gradle-cpp-cross-platform/workflows/Gradle%20build/badge.svg)](https://github.com/Anatolii/gradle-cpp-cross-platform/actions)

Gradle plugin which enables cross-platform compilation of C/C++ code for Android targets using new plugins for native compilation.

The plugin is built as extension to the [new C++ plugins](https://blog.gradle.org/update-on-the-new-cpp-plugins).

## Plugin ID

```text
dev.anatolii.cpp.android.toolchain
```

## Publishing locally

```shell script
./gradlew :plugin:publishToMavenLocal
```

In order to find out the version which was published run following task:

```shell script
./gradlew :plugin:properties
```

The version will be printed among the project properties.

## Usage example

**NOTE**: In case publication to Maven repository is set up, packages will be published for the same OS as the host they were produced from.
While the actual `.so` or `.a` files should be suitable for usual Android apps development,
packages published to Maven will be tight to the host OS due to lack of cross-platform compilation support in Gradle.

It is possible to apply Android related configuration as following example shows:

```kotlin
plugins {
    id("cpp-library")
    id("dev.anatolii.cpp.android.toolchain")
}

libraryAndroid {
    // Required if not specified manually, like it shown on example bellow
    apis = listOf(21, 25, 29) // default is empty list
    // Optional
    abis = listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64") // this is default value
    stl = "c++_static" // default value is "c++_shared"
    ndkDir = File("path/to/Android/NDK") // if null, then tries to find NDK in as part of SDK, or in local.properties file set as ndk.dir, or as environment variable ANDROID_NDK_HOME
    sdkDir = File("path/to/Android/SDK") // by default tries to find SDK in local.properties file set as sdk.dir, or as environment variable ANDROID_HOME
    ndkVersion = "20.0.5594570" // specify version of NDK which you have in AndroidSDK/ndk/ folder
    isNeon = false // this is default value
    disableFormatStringChecks = false // this is default value
    armMode = "thumb" // this is default value
    forceCppFeatures = false // this is default value
    cppFeatures = listOf("rtti", "exceptions") // default is empty list
}
```

You can also specify target Android machines like you would otherwise do for Linux, macOS or Windows.

Use following template to define architecture specifically for Android:

```text
android_<api>_<abi>
```

Where 
- `<api>` can be any supported by Android NDK 
- `<abi>` any of `armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`

The `android` prefix must be used to specify architecture of the target machine, so that CPP Android ToolChain will be able to distinguish it from other architectures for which you might be building your source code.

```kotlin
plugins {
    id("cpp-library")
    id("dev.anatolii.cpp.android.toolchain")
}
// ...
library {
    // Alternatively you can set the target architecture as string
    targetMachines.add(machines.linux.architecture("android_28_x86"))
    // Pre-defined architectures will be configured by "cpp-library" plugin as usually.
    targetMachines.add(machines.linux.x86_64)

    linkage.add(Linkage.SHARED)
    // and / or
    linkage.add(Linkage.STATIC)
}
```

## Samples

There are samples available to demonstrate usage of this plugin. Read more: [samples/Readme](samples/README.md)
