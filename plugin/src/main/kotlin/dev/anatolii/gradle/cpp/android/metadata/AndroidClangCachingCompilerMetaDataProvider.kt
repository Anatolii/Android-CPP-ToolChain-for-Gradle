package dev.anatolii.gradle.cpp.android.metadata

import org.gradle.api.Action
import org.gradle.nativeplatform.toolchain.internal.metadata.AbstractMetadataProvider
import org.gradle.nativeplatform.toolchain.internal.metadata.CompilerMetaDataProvider
import org.gradle.nativeplatform.toolchain.internal.metadata.CompilerType
import org.gradle.platform.base.internal.toolchain.SearchResult
import java.io.File
import java.util.*

class AndroidClangCachingCompilerMetaDataProvider<T : AndroidClangMetaData> constructor(private val delegate: CompilerMetaDataProvider<T>) : CompilerMetaDataProvider<T> {

    private val resultMap = HashMap<Key, SearchResult<T>>()

    override fun getCompilerMetaData(path: List<File>, configureAction: Action<in CompilerMetaDataProvider.CompilerExecSpec>): SearchResult<T>? {
        val execSpec = AbstractMetadataProvider.DefaultCompilerExecSpec()
        configureAction.execute(execSpec)

        val key = Key(execSpec.executable, execSpec.args, path, execSpec.environments)
        var result: SearchResult<T>? = resultMap[key]
        if (result == null) {
            result = delegate.getCompilerMetaData(path, configureAction)
            resultMap[key] = result
        }
        return result
    }

    override fun getCompilerType(): CompilerType {
        return delegate.compilerType
    }
}

