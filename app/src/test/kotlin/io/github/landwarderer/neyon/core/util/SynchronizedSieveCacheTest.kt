package io.github.landwarderer.neyon.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import java.util.concurrent.atomic.AtomicInteger

class SynchronizedSieveCacheTest {

    @Test
    fun `test put and get`() {
        val cache = SynchronizedSieveCache<String, String>(5)
        cache.put("key1", "value1")
        assertEquals("value1", cache["key1"])
        assertNull(cache["key2"])
    }

    @Test
    fun `test remove and evictAll`() {
        val cache = SynchronizedSieveCache<String, String>(5)
        cache.put("key1", "value1")
        cache.put("key2", "value2")

        cache.remove("key1")
        assertNull(cache["key1"])
        assertEquals("value2", cache["key2"])

        cache.evictAll()
        assertNull(cache["key2"])
    }

    @Test
    fun `test maxSize eviction`() {
        val cache = SynchronizedSieveCache<Int, String>(2)
        cache.put(1, "one")
        cache.put(2, "two")
        cache.put(3, "three")

        // 1 Should be evicted due to size constraints
        assertNull(cache[1])
        assertEquals("two", cache[2])
        assertEquals("three", cache[3])
    }

    @Test
    fun `test thread safety under concurrent environment`() = runTest {
        val cache = SynchronizedSieveCache<Int, Int>(1000)
        
        // Concurrently populate the cache
        val jobs = (1..100).map { i ->
            async {
                cache.put(i, i)
            }
        }
        jobs.awaitAll()

        // Should securely reach 100 entries, or up to maxSize if less
        var hitCount = 0
        for (i in 1..100) {
            if (cache[i] == i) {
                hitCount++
            }
        }
        assertEquals(100, hitCount)
    }

    @Test
    fun `test removeIf`() {
        val cache = SynchronizedSieveCache<Int, String>(5)
        cache.put(1, "1")
        cache.put(2, "2")
        cache.put(3, "3")

        cache.removeIf { key, _ -> key % 2 != 0 }

        assertNull(cache[1])
        assertEquals("2", cache[2])
        assertNull(cache[3])
    }

    @Test
    fun `test trimToSize`() {
        val cache = SynchronizedSieveCache<Int, String>(5)
        cache.put(1, "one")
        cache.put(2, "two")
        cache.put(3, "three")

        cache.trimToSize(1)
        
        // Retains the most recently added items, only mapping for 3 should exist here based on standard sizing
        assertNull(cache[1])
        assertNull(cache[2])
        assertEquals("three", cache[3])
    }
}
