package io.github.landwarderer.futon.core.cache

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import java.util.concurrent.TimeUnit
import android.os.SystemClock
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Before

class ExpiringValueTest {
    private var currentTime = 1000L

    @Before
    fun setUp() {
        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } answers { currentTime }
    }

    @After
    fun tearDown() {
        unmockkStatic(SystemClock::class)
    }

    @Test
    fun testExpiringValue() {
        val value = ExpiringValue("test", 1000, TimeUnit.MILLISECONDS)
        assertEquals("test", value.get())
        assertFalse(value.isExpired)
    }

    @Test
    fun testExpiringValueExpired() {
        val value = ExpiringValue("test", 1000, TimeUnit.MILLISECONDS)
        currentTime += 1001

        assertTrue(value.isExpired)
        assertNull(value.get())
    }

    @Test
    fun testEqualsAndHashCode() {
        val value1 = ExpiringValue("test", 1000, TimeUnit.MILLISECONDS)
        val value2 = ExpiringValue("test", 1000, TimeUnit.MILLISECONDS)
        val value3 = ExpiringValue("other", 1000, TimeUnit.MILLISECONDS)

        currentTime += 10
        val value4 = ExpiringValue("test", 1000, TimeUnit.MILLISECONDS)

        assertEquals(value1, value1)
        assertEquals(value1, value2)
        assertEquals(value1.hashCode(), value2.hashCode())

        assertNotEquals(value1, value3)
        assertNotEquals(value1, value4)
        assertNotEquals(value1, "test")
        assertNotEquals(value1, null)
    }
}
