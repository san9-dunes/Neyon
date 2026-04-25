package io.github.landwarderer.neyon.mihon

import android.content.Context
import android.util.Log
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.landwarderer.neyon.core.network.MangaHttpClient
import io.github.landwarderer.neyon.core.network.webview.WebViewExecutor
import io.github.landwarderer.neyon.mihon.compat.MihonInjektBridge
import io.github.landwarderer.neyon.mihon.extensions.repo.ExternalExtensionRepoRepository
import io.github.landwarderer.neyon.mihon.extensions.repo.InstalledExtensionSignatureValidator
import kotlinx.serialization.json.Json
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MihonModule {
    @Provides
    @Singleton
    fun provideMihonJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Provides
    @Singleton
    fun provideMihonInjektBridge(
        @ApplicationContext context: Context,
        @MangaHttpClient okHttpClient: OkHttpClient,
        cookieJar: CookieJar,
        webViewExecutor: WebViewExecutor,
    ): MihonInjektBridge {
        return try {
            MihonInjektBridge(
                context = context,
                httpClient = okHttpClient,
                cookieJar = cookieJar,
                webViewExecutor = webViewExecutor,
            )
        } catch (e: Throwable) {
            Log.e("MihonModule", "CRITICAL ERROR: Failed to create MihonInjektBridge!", e)
            // Still need to return something or Dagger will fail. 
            // In case of fatal libs issue (NoClassDefFound), this might still crash later, 
            // but let's try to catch it here.
            throw e
        }
    }

    @Provides
    @Singleton
    fun provideMihonExtensionLoader(
        @ApplicationContext context: Context,
        injektBridge: dagger.Lazy<MihonInjektBridge>,
        repoRepository: ExternalExtensionRepoRepository,
        signatureValidator: InstalledExtensionSignatureValidator,
    ): MihonExtensionLoader {
        return MihonExtensionLoader(context, injektBridge, repoRepository, signatureValidator)
    }

    @Provides
    @Singleton
    fun provideMihonExtensionManager(
        @ApplicationContext context: Context,
        loader: MihonExtensionLoader,
    ): MihonExtensionManager {
        return MihonExtensionManager(context, loader)
    }
}
