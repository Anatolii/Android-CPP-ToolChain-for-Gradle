# Boost

Project source code can be found on GitHub: https://github.com/boostorg/boost

[![Build](https://github.com/Anatolii/gradle-cpp-cross-platform/workflows/Boost/badge.svg)](https://github.com/Anatolii/gradle-cpp-cross-platform/actions)

**Note**: All commands below assume that you execute them from parent folder `samples`.

## Initial setup

In case you use only command line tools to explore this project, you need first to execute Gradle build without any tasks specified.
This way required Boost modules description will be downloaded and so rest of the project could be configured.

```shell script
../gradlew
```

If you opened Samples project in IDE (from the `samples` folder as root) then all necessary preparation should be done for you. In all other cases you should follow instruction above.

## Download upstream sources

In order to configure Boost example you need first to execute `downloadUpstream` task for `boost` project.

```shell script
../gradlew :boost:downloadUpstream
```

## Change version of Boost

The version is set in [gradle.properties](gradle.properties) file.

## Compile boost modules

Full list of modules can be found here: https://github.com/boostorg/boost/blob/master/.gitmodules

Following command will assemble `assert` module's `debug` variant as a `shared` library for `Android API 29` and `arm64-v8a` architecture.

```shell script
../gradlew :boost:assert:assembleDebugSharedAndroid_29_arm64-v8a
```

Following command will assemble `assert` module for all variants and all Android APIs which are configured.

```shell script
../gradlew :boost:assert:assemble
```