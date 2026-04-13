package io.github.landwarderer.futon.core.cache

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeDeferredTest {

	@Test
	fun `await returns value on success`() = runTest {
		val deferred = CompletableDeferred<Result<String>>()
		deferred.complete(Result.success("success"))
		val safeDeferred = SafeDeferred(deferred)

		assertEquals("success", safeDeferred.await())
	}

	@Test
	fun `await throws exception on failure`() = runTest {
		val deferred = CompletableDeferred<Result<String>>()
		deferred.complete(Result.failure(RuntimeException("error")))
		val safeDeferred = SafeDeferred(deferred)

		var thrownException: Throwable? = null
		try {
			safeDeferred.await()
		} catch (e: Throwable) {
			thrownException = e
		}

		assertTrue(thrownException is RuntimeException)
		assertEquals("error", thrownException?.message)
	}

	@Test
	fun `awaitOrNull returns value on success`() = runTest {
		val deferred = CompletableDeferred<Result<String>>()
		deferred.complete(Result.success("success"))
		val safeDeferred = SafeDeferred(deferred)

		assertEquals("success", safeDeferred.awaitOrNull())
	}

	@Test
	fun `awaitOrNull returns null on failure`() = runTest {
		val deferred = CompletableDeferred<Result<String>>()
		deferred.complete(Result.failure(RuntimeException("error")))
		val safeDeferred = SafeDeferred(deferred)

		assertNull(safeDeferred.awaitOrNull())
	}

	@Test
	fun `cancel cancels the delegate`() = runTest {
		val deferred = CompletableDeferred<Result<String>>()
		val safeDeferred = SafeDeferred(deferred)

		safeDeferred.cancel()

		assertTrue(deferred.isCancelled)
	}
}
