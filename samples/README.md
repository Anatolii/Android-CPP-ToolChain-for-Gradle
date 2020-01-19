# Sample projects

Samples demonstrate usage of plugin on a few open source projects.
The projects are downloaded from upstream repositories and used untouched.

All examples below will assume that you run them from current `samples` directory. 

In order to list sub-projects in samples project using Gradle Wrapper you have at least two options:

```shell script
# inside samples direcotry
../gradlew projects
# From Git repository root directory
./gradlew --build-file samples/build.gradle.kts projects
```

## Play with configurations

You can modify configuration of Android CPP ToolChain plugin in [build.gradle.kts](build.gradle.kts) file.

## Boost

Read more in the sample's [README.md](boost/README.md) file.