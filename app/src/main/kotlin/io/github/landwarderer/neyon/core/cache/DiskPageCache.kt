package io.github.landwarderer.neyon.core.cache

import android.app.Application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaSource
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiskPageCache @Inject constructor(application: Application) {
	private val cacheDir = File(application.cacheDir, "pages_cache").apply { mkdirs() }
	private val maxFiles = 2000

	suspend fun getPages(source: MangaSource, url: String): List<MangaPage>? = withContext(Dispatchers.IO) {
		val file = getFile(source, url)
		if (!file.exists()) return@withContext null

		try {
			val jsonArray = JSONArray(file.readText())
			val pages = ArrayList<MangaPage>(jsonArray.length())
			for (i in 0 until jsonArray.length()) {
				val obj = jsonArray.getJSONObject(i)
				pages.add(
					MangaPage(
						id = obj.getLong("id"),
						url = obj.getString("url"),
						preview = if (obj.has("preview")) obj.getString("preview") else null,
						source = source
					)
				)
			}
			file.setLastModified(System.currentTimeMillis())
			pages
		} catch (e: Exception) {
			file.delete()
			null
		}
	}

	suspend fun putPages(source: MangaSource, url: String, pages: List<MangaPage>) = withContext(Dispatchers.IO) {
		try {
			cacheDir.mkdirs()
			val jsonArray = JSONArray()
			for (page in pages) {
				val obj = JSONObject()
				obj.put("id", page.id)
				obj.put("url", page.url)
				if (page.preview != null) {
					obj.put("preview", page.preview)
				}
				jsonArray.put(obj)
			}

			val file = getFile(source, url)
			file.writeText(jsonArray.toString())

			trimCache()
		} catch (e: Exception) {
			// Ignore
		}
	}

	private fun getFile(source: MangaSource, url: String): File {
		val key = "${source.name}_$url"
		val hash = hashString(key)
		return File(cacheDir, hash)
	}

	private fun trimCache() {
		val files = cacheDir.listFiles() ?: return
		if (files.size > maxFiles) {
			files.sortBy { it.lastModified() }
			val toDelete = files.size - maxFiles
			for (i in 0 until toDelete) {
				files[i].delete()
			}
		}
	}

	private fun hashString(input: String): String {
		val digest = MessageDigest.getInstance("MD5")
		val bytes = digest.digest(input.toByteArray())
		return bytes.joinToString("") { "%02x".format(it) }
	}
}
