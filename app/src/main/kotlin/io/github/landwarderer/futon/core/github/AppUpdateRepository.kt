package io.github.landwarderer.futon.core.github

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.landwarderer.futon.BuildConfig
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.core.network.BaseHttpClient
import io.github.landwarderer.futon.core.prefs.AppSettings
import io.github.landwarderer.futon.core.util.ext.printStackTraceDebug
import org.koitharu.kotatsu.parsers.util.await
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private const val BUILD_TYPE_RELEASE = "release"

@Singleton
class AppUpdateRepository @Inject constructor(
	private val settings: AppSettings,
	@BaseHttpClient private val okHttp: OkHttpClient,
	@ApplicationContext context: Context,
) {
	private val availableUpdate = MutableStateFlow<AppVersion?>(null)

	private val latestReleaseUrl = buildString {
		append("https://api.github.com/repos/")
		append(context.getString(R.string.github_updates_repo))
		append("/releases")
	}

	private val changelogUrl = buildString {
		append("https://raw.githubusercontent.com/")
		append(context.getString(R.string.github_updates_repo))
		append("/refs/heads/devel/CHANGELOG.md")
	}

	val isUpdateAvailable: Boolean
		get() = availableUpdate.value != null

	fun observeAvailableUpdate() = availableUpdate.asStateFlow()

	suspend fun fetchUpdate(): AppVersion? = withContext(Dispatchers.IO) {
		runCatchingCancellable {
			val isNightly = BuildConfig.BUILD_TYPE != BUILD_TYPE_RELEASE
			val isUnstableAllowed = settings.isUnstableUpdatesAllowed
			val url = if (isNightly || isUnstableAllowed) {
				latestReleaseUrl // Fetch all releases and find the latest one we can use
			} else {
				"$latestReleaseUrl/latest" // Fetch only stable latest release
			}

			val request = Request.Builder()
				.get()
				.url(url)
				.build()
			val response = okHttp.newCall(request).await()
			val responseString = response.body?.string() ?: return@runCatchingCancellable null

			val json = if (isNightly || isUnstableAllowed) {
				val jsonArray = org.json.JSONArray(responseString)
				if (jsonArray.length() == 0) return@runCatchingCancellable null
				jsonArray.getJSONObject(0)
			} else {
				JSONObject(responseString)
			}
			
			val currentVersion = VersionId(BuildConfig.VERSION_NAME)
			val releaseVersion = VersionId(json.getString("tag_name").removePrefix("v"))
			
			// Only return update if there's a newer version available
			if (releaseVersion <= currentVersion) {
				return@runCatchingCancellable null
			}
			
			val assets = json.optJSONArray("assets")
			var apkSize = 0L
			var apkUrl = ""

			if (assets != null && assets.length() > 0) {
				for (i in 0 until assets.length()) {
					val asset = assets.getJSONObject(i)
					if (asset.getString("name").endsWith(".apk")) {
						apkSize = asset.getLong("size")
						apkUrl = asset.getString("browser_download_url")
						break
					}
				}
			}

			AppVersion(
				id = json.getLong("id"),
				url = json.getString("html_url"),
				name = json.getString("name").removePrefix("v"),
				apkSize = apkSize,
				apkUrl = apkUrl,
				description = json.getString("body"),
			)
		}.onFailure {
			it.printStackTraceDebug("AppUpdateRepository::fetchUpdate")
		}.onSuccess {
			availableUpdate.value = it
		}.getOrNull()
	}

	suspend fun fetchChangelog(): String? = withContext(Dispatchers.IO) {
		runCatchingCancellable {
			val request = Request.Builder()
				.get()
				.url(changelogUrl)
				.build()
			okHttp.newCall(request).await().body?.string()
		}.onFailure {
			it.printStackTraceDebug("AppUpdateRepository::fetchChangelog")
		}.getOrNull()
	}

	@Suppress("KotlinConstantConditions")
	suspend fun isUpdateSupported(): Boolean {
		return true // Updates are always available now (just checking for newer version)
	}
}
