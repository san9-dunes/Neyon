package io.github.landwarderer.neyon.core.util

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class MultiMutexTest {

    @Test
    fun `test basic lock and unlock`() = runTest {
        val mutex = MultiMutex<String>()
        val key = "test_key"
        
        assertTrue(mutex.isEmpty())
        assertFalse(mutex.isNotEmpty())
        assertEquals(0, mutex.size)

        mutex.lock(key)
        assertFalse(mutex.isEmpty())
        assertTrue(mutex.isNotEmpty())
        assertEquals(1, mutex.size)

        mutex.unlock(key)
        assertTrue(mutex.isEmpty())
        assertFalse(mutex.isNotEmpty())
        assertEquals(0, mutex.size)
    }

    @Test
    fun `test sequential execution with withLock`() = runTest {
        val mutex = MultiMutex<String>()
        val key = "test_key"
        val counter = AtomicInteger(0)

        // Run 10 parallel jobs acquiring the same lock
        val jobs = (1..10).map {
            async {
                mutex.withLock(key) {
                    val current = counter.get()
                    // If locks are working, no other thread will modify counter during this delay
                    delay(10) 
                    counter.set(current + 1)
                }
            }
        }
        jobs.awaitAll()

        // All 10 increments should succeed and none should be lost
        assertEquals(10, counter.get())
        assertTrue(mutex.isEmpty())
    }

    @Test
    fun `test multiple locks with different keys`() = runTest {
        val mutex = MultiMutex<String>()
        val key1 = "test_key_1"
        val key2 = "test_key_2"

        mutex.lock(key1)
        assertEquals(1, mutex.size)
        
        mutex.lock(key2)
        assertEquals(2, mutex.size)

        mutex.unlock(key1)
        assertEquals(1, mutex.size)
        
        mutex.unlock(key2)
        assertEquals(0, mutex.size)
    }
}
