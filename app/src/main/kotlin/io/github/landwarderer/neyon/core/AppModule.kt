package io.github.landwarderer.neyon.core

import android.app.Application
import android.content.Context
import android.os.Build
import android.provider.SearchRecentSuggestions
import android.text.Html
import androidx.collection.arraySetOf
import androidx.core.content.ContextCompat
import androidx.room.InvalidationTracker
import androidx.work.WorkManager
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.allowRgb565
import coil3.svg.SvgDecoder
import coil3.util.DebugLogger
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.ElementsIntoSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.OkHttpClient
import io.github.landwarderer.neyon.BuildConfig
import io.github.landwarderer.neyon.backups.domain.BackupObserver
import io.github.landwarderer.neyon.core.db.MangaDatabase
import io.github.landwarderer.neyon.core.exceptions.resolve.CaptchaHandler
import io.github.landwarderer.neyon.core.image.AvifImageDecoder
import io.github.landwarderer.neyon.core.image.CbzFetcher
import io.github.landwarderer.neyon.core.image.MangaSourceHeaderInterceptor
import io.github.landwarderer.neyon.core.network.MangaHttpClient
import io.github.landwarderer.neyon.core.network.imageproxy.ImageProxyInterceptor
import io.github.landwarderer.neyon.core.os.AppShortcutManager
import io.github.landwarderer.neyon.core.os.NetworkState
import io.github.landwarderer.neyon.core.parser.MangaLoaderContextImpl
import io.github.landwarderer.neyon.core.parser.favicon.FaviconFetcher
import io.github.landwarderer.neyon.core.prefs.AppSettings
import io.github.landwarderer.neyon.core.ui.image.CoilImageGetter
import io.github.landwarderer.neyon.core.ui.util.ActivityRecreationHandle

import io.github.landwarderer.neyon.core.util.FileSize
import io.github.landwarderer.neyon.core.util.ext.connectivityManager
import io.github.landwarderer.neyon.core.util.ext.isLowRamDevice
import io.github.landwarderer.neyon.details.ui.pager.pages.MangaPageFetcher
import io.github.landwarderer.neyon.details.ui.pager.pages.MangaPageKeyer
import io.github.landwarderer.neyon.local.data.CacheDir
import io.github.landwarderer.neyon.local.data.FaviconCache
import io.github.landwarderer.neyon.local.data.LocalStorageCache
import io.github.landwarderer.neyon.local.data.LocalStorageChanges
import io.github.landwarderer.neyon.local.data.PageCache
import io.github.landwarderer.neyon.local.domain.model.LocalManga
import io.github.landwarderer.neyon.main.domain.CoverRestoreInterceptor
import io.github.landwarderer.neyon.main.ui.protect.AppProtectHelper
import io.github.landwarderer.neyon.main.ui.protect.ScreenshotPolicyHelper
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import io.github.landwarderer.neyon.search.ui.MangaSuggestionsProvider
import io.github.landwarderer.neyon.sync.domain.SyncController
import io.github.landwarderer.neyon.widget.WidgetUpdater
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface AppModule {

	@Binds
	fun bindMangaLoaderContext(mangaLoaderContextImpl: MangaLoaderContextImpl): MangaLoaderContext

	@Binds
	fun bindImageGetter(coilImageGetter: CoilImageGetter): Html.ImageGetter

	companion object {

		@Provides
		@LocalizedAppContext
		fun provideLocalizedContext(
			@ApplicationContext context: Context,
		): Context = ContextCompat.getContextForLanguage(context)

		@Provides
		@Singleton
		fun provideNetworkState(
			@ApplicationContext context: Context,
			settings: AppSettings,
		) = NetworkState(context.connectivityManager, settings)

		@Provides
		@Singleton
		fun provideMangaDatabase(
			@ApplicationContext context: Context,
		): MangaDatabase = MangaDatabase(context)

		@Provides
		@Singleton
		fun provideCoil(
			@LocalizedAppContext context: Context,
			@MangaHttpClient okHttpClientProvider: Provider<OkHttpClient>,
			faviconFetcherFactory: FaviconFetcher.Factory,
			imageProxyInterceptor: ImageProxyInterceptor,
			pageFetcherFactory: MangaPageFetcher.Factory,
			coverRestoreInterceptor: CoverRestoreInterceptor,
			networkStateProvider: Provider<NetworkState>,
			captchaHandler: CaptchaHandler,
		): ImageLoader {
			val diskCacheFactory = {
				val rootDir = context.externalCacheDir ?: context.cacheDir
				DiskCache.Builder()
					.directory(rootDir.resolve(CacheDir.THUMBS.dir))
					.build()
			}
			val okHttpClientLazy = lazy {
				okHttpClientProvider.get().newBuilder().cache(null).build()
			}
			return ImageLoader.Builder(context)
				.interceptorCoroutineContext(Dispatchers.IO)
				.diskCache(diskCacheFactory)
				.logger(if (BuildConfig.DEBUG) DebugLogger() else null)
				.allowRgb565(context.isLowRamDevice())
				.eventListener(captchaHandler)
				.components {
					add(
						OkHttpNetworkFetcherFactory(
							callFactory = okHttpClientLazy::value,
							connectivityChecker = { networkStateProvider.get() },
						),
					)
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
						add(AnimatedImageDecoder.Factory())
					} else {
						add(GifDecoder.Factory())
					}
					add(SvgDecoder.Factory())
					add(CbzFetcher.Factory())
					add(AvifImageDecoder.Factory())
					add(faviconFetcherFactory)
					add(MangaPageKeyer())
					add(pageFetcherFactory)
					add(imageProxyInterceptor)
					add(coverRestoreInterceptor)
					add(MangaSourceHeaderInterceptor())
				}.build()
		}

		@Provides
		fun provideSearchSuggestions(
			@ApplicationContext context: Context,
		): SearchRecentSuggestions = MangaSuggestionsProvider.createSuggestions(context)

		@Provides
		@ElementsIntoSet
		fun provideDatabaseObservers(
			widgetUpdater: WidgetUpdater,
			appShortcutManager: AppShortcutManager,
			backupObserver: BackupObserver,
			syncController: SyncController,
		): Set<@JvmSuppressWildcards InvalidationTracker.Observer> = arraySetOf(
			widgetUpdater,
			appShortcutManager,
			backupObserver,
			syncController,
		)

		@Provides
		@ElementsIntoSet
		fun provideActivityLifecycleCallbacks(
			appProtectHelper: AppProtectHelper,
			activityRecreationHandle: ActivityRecreationHandle,
			screenshotPolicyHelper: ScreenshotPolicyHelper,
		): Set<@JvmSuppressWildcards Application.ActivityLifecycleCallbacks> = arraySetOf(
			appProtectHelper,
			activityRecreationHandle,
			screenshotPolicyHelper,
		) 

		@Provides
		@Singleton
		@LocalStorageChanges
		fun provideMutableLocalStorageChangesFlow(): MutableSharedFlow<LocalManga?> = MutableSharedFlow()

		@Provides
		@LocalStorageChanges
		fun provideLocalStorageChangesFlow(
			@LocalStorageChanges flow: MutableSharedFlow<LocalManga?>,
		): SharedFlow<LocalManga?> = flow.asSharedFlow()

		@Provides
		fun provideWorkManager(
			@ApplicationContext context: Context,
		): WorkManager = WorkManager.getInstance(context)

		@Provides
		@Singleton
		@PageCache
		fun providePageCache(
			@ApplicationContext context: Context,
			settings: AppSettings,
		): LocalStorageCache {
			val userLimit = settings.pagesCacheSizeMB.toLong()
			val defaultSize = if (userLimit == -1L) Long.MAX_VALUE else FileSize.MEGABYTES.convert(userLimit, FileSize.BYTES)
			return LocalStorageCache(
				context = context,
				dir = CacheDir.PAGES,
				defaultSize = defaultSize,
				minSize = FileSize.MEGABYTES.convert(20, FileSize.BYTES),
			)
		}

		@Provides
		@Singleton
		@FaviconCache
		fun provideFaviconCache(
			@ApplicationContext context: Context,
		) = LocalStorageCache(
			context = context,
			dir = CacheDir.FAVICONS,
			defaultSize = FileSize.MEGABYTES.convert(8, FileSize.BYTES),
			minSize = FileSize.MEGABYTES.convert(2, FileSize.BYTES),
		)
	}
}
