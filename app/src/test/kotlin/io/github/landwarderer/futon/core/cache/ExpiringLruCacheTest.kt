package io.github.landwarderer.futon.core.cache

import org.junit.Test
import org.junit.Assert.*
import java.util.concurrent.TimeUnit
import io.github.landwarderer.futon.core.cache.MemoryContentCache.Key as CacheKey
import io.github.landwarderer.futon.core.model.LocalMangaSource
import io.github.landwarderer.futon.core.model.TestMangaSource

class ExpiringLruCacheTest {

    private var currentTime = 0L
    private val timeProvider: () -> Long = { currentTime }

    @Test
    fun testCachePutAndGet() {
        val cache = ExpiringLruCache<String>(10, 10, TimeUnit.SECONDS, timeProvider)
        val key = CacheKey(LocalMangaSource, "url1")

        cache[key] = "value1"
        assertEquals("value1", cache[key])
    }

    @Test
    fun testExpiration() {
        val cache = ExpiringLruCache<String>(10, 10, TimeUnit.SECONDS, timeProvider)
        val key = CacheKey(LocalMangaSource, "url1")

        cache[key] = "value1"
        assertEquals("value1", cache[key])

        currentTime += 10001 // More than 10 seconds
        assertNull(cache[key])
    }

    @Test
    fun testClear() {
        val cache = ExpiringLruCache<String>(10, 10, TimeUnit.SECONDS, timeProvider)
        val key1 = CacheKey(LocalMangaSource, "url1")

        cache[key1] = "value1"
        cache.clear()
        assertNull(cache[key1])
    }

    @Test
    fun testRemove() {
        val cache = ExpiringLruCache<String>(10, 10, TimeUnit.SECONDS, timeProvider)
        val key1 = CacheKey(LocalMangaSource, "url1")
        val key2 = CacheKey(LocalMangaSource, "url2")

        cache[key1] = "value1"
        cache[key2] = "value2"

        cache.remove(key1)
        assertNull(cache[key1])
        assertEquals("value2", cache[key2])
    }

    @Test
    fun testRemoveAll() {
        val cache = ExpiringLruCache<String>(10, 10, TimeUnit.SECONDS, timeProvider)
        val key1 = CacheKey(LocalMangaSource, "url1")
        val key2 = CacheKey(LocalMangaSource, "url2")
        val key3 = CacheKey(TestMangaSource, "url3")

        cache[key1] = "value1"
        cache[key2] = "value2"
        cache[key3] = "value3"

        cache.removeAll(LocalMangaSource)
        assertNull(cache[key1])
        assertNull(cache[key2])
        assertEquals("value3", cache[key3])
    }

    @Test
    fun testTrimToSize() {
        val cache = ExpiringLruCache<String>(10, 10, TimeUnit.SECONDS, timeProvider)
        val key1 = CacheKey(LocalMangaSource, "url1")
        val key2 = CacheKey(LocalMangaSource, "url2")
        val key3 = CacheKey(LocalMangaSource, "url3")

        cache[key1] = "value1"
        cache[key2] = "value2"
        cache[key3] = "value3"

        cache.trimToSize(2)
        // LRU evicts the oldest elements first
        assertNull(cache[key1])
        assertEquals("value2", cache[key2])
        assertEquals("value3", cache[key3])
    }
}
