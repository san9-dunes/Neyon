package io.github.landwarderer.futon.core.cache

import android.app.Application
import io.github.landwarderer.futon.core.model.TestMangaSource
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.koitharu.kotatsu.parsers.model.MangaPage
import java.io.File

class DiskPageCacheTest {

    private lateinit var cacheDir: File
    private lateinit var application: FakeApplication
    private lateinit var diskPageCache: DiskPageCache

    // Simple fake class to avoid using Mockito since it's not present
    class FakeApplication(private val cacheDirectory: File) : Application() {
        override fun getCacheDir(): File {
            return cacheDirectory
        }
    }

    @Before
    fun setUp() {
        val tempDir = System.getProperty("java.io.tmpdir") ?: "/tmp"
        application = FakeApplication(File(tempDir))
        diskPageCache = DiskPageCache(application)

        // Need to get the actual cache dir created by DiskPageCache
        cacheDir = File(application.cacheDir, "pages_cache")
        cacheDir.mkdirs()
    }

    @After
    fun tearDown() {
        cacheDir.deleteRecursively()
    }

    @Test
    fun `getPages deletes file and returns null when json is malformed`() = runTest {
        val source = TestMangaSource
        val url = "http://example.com/chapter1"

        // Use putPages to create the valid structure
        val dummyPages = listOf(MangaPage(id = 1L, url = "http://example.com/image1.jpg", preview = null, source = source))
        diskPageCache.putPages(source, url, dummyPages)

        // Find the created file and overwrite it with malformed JSON
        val cachedFiles = cacheDir.listFiles()
        requireNotNull(cachedFiles)
        require(cachedFiles.isNotEmpty()) { "Cache file should have been created" }

        val file = cachedFiles[0]
        file.writeText("invalid json content")

        val result = diskPageCache.getPages(source, url)

        assertNull("Result should be null when JSON is malformed", result)
        assertFalse("File should be deleted when Exception occurs", file.exists())
    }
}
