package io.github.landwarderer.neyon.core.network

import dagger.Lazy
import io.github.landwarderer.neyon.BuildConfig
import io.github.landwarderer.neyon.core.model.MangaSource
import io.github.landwarderer.neyon.core.parser.MangaLoaderContextImpl
import io.github.landwarderer.neyon.core.parser.MangaRepository
import io.github.landwarderer.neyon.core.parser.ParserMangaRepository
import io.github.landwarderer.neyon.core.util.ext.printStackTraceDebug
import io.github.landwarderer.neyon.mihon.MihonMangaRepository
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.util.mergeWith
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Interceptor.Chain
import okhttp3.Request
import okhttp3.Response
import okio.IOException
import java.net.IDN
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommonHeadersInterceptor @Inject constructor(
	private val mangaRepositoryFactoryLazy: Lazy<MangaRepository.Factory>,
	private val mangaLoaderContextLazy: Lazy<MangaLoaderContextImpl>,
) : Interceptor {

	override fun intercept(chain: Chain): Response {
		val request = chain.request()
		val source = request.tag(MangaSource::class.java)
			?: request.headers[CommonHeaders.MANGA_SOURCE]?.let { MangaSource(it) }
		val parserRepository = when {
			source is MangaParserSource -> {
				mangaRepositoryFactoryLazy.get().create(source) as? ParserMangaRepository
			}
			else -> null
		}
		val mihonRepository = when {
			source?.name?.startsWith("MIHON_") == true -> {
				mangaRepositoryFactoryLazy.get().create(source) as? MihonMangaRepository
			}
			else -> {
				if (BuildConfig.DEBUG && source == null) {
					IllegalArgumentException("Request without source tag: ${request.url}")
						.printStackTrace()
				}
				null
			}
		}
		val headersBuilder = request.headers.newBuilder()
			.removeAll(CommonHeaders.MANGA_SOURCE)
		val extraHeaders: Headers? = parserRepository?.getRequestHeaders()
			?: mihonRepository?.getRequestHeaders()?.let { map ->
				Headers.Builder().apply { map.forEach { (k, v) -> add(k, v) } }.build()
			}
		extraHeaders?.let { headersBuilder.mergeWith(it, replaceExisting = false) }
		if (headersBuilder[CommonHeaders.USER_AGENT] == null) {
			headersBuilder[CommonHeaders.USER_AGENT] = mangaLoaderContextLazy.get().getDefaultUserAgent()
		}
		if (headersBuilder[CommonHeaders.REFERER] == null && parserRepository != null) {
			val idn = IDN.toASCII(parserRepository.domain)
			headersBuilder.trySet(CommonHeaders.REFERER, "https://$idn/")
		}
		val newRequest = request.newBuilder().headers(headersBuilder.build()).build()
		return parserRepository?.interceptSafe(ProxyChain(chain, newRequest)) ?: chain.proceed(newRequest)
	}

	private fun Headers.Builder.trySet(name: String, value: String) = try {
		set(name, value)
	} catch (e: IllegalArgumentException) {
		e.printStackTraceDebug("CommonHeadersInterceptor::trySet")
	}

	private fun Interceptor.interceptSafe(chain: Chain): Response = runCatchingCancellable {
		intercept(chain)
	}.getOrElse { e ->
		if (e is IOException || e is Error) {
			throw e
		} else {
			// only IOException can be safely thrown from an Interceptor
			throw IOException("Error in interceptor: ${e.message}", e)
		}
	}

	private class ProxyChain(
		private val delegate: Chain,
		private val request: Request,
	) : Chain by delegate {

		override fun request(): Request = request
	}
}
