import java.net.URL

plugins {
    id("dev.anatolii.internal.upstream")
    id("cpp-library")
    id("dev.anatolii.cpp.android.toolchain")
}

upstream {
    sourcesZipUrl = URL("https://github.com/unicode-org/icu/releases/download/release-${version.toString().replace('.', '-')}/icu4c-${version.toString().replace('.', '_')}-src.zip")
}

val baseSourcesDir = file("${upstream.extractDir}/icu/source")
extensions.configure<CppLibrary> {
    source.from(
            "${baseSourcesDir}/common",
            "${baseSourcesDir}/i18n",
            "${baseSourcesDir}/io",
            "${baseSourcesDir}/layoutex"
    )
    publicHeaders {
        this.from(
                "${baseSourcesDir}/common",
                "${baseSourcesDir}/i18n",
                "${baseSourcesDir}/io",
                "${baseSourcesDir}/layoutex"
        )
    }
}
