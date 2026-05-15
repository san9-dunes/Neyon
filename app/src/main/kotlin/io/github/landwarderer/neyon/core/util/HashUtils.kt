package io.github.landwarderer.neyon.core.util

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object HashUtils {
	private const val ITERATIONS = 100_000
	private const val KEY_LENGTH = 160
	private const val SALT_LENGTH = 16
	private const val ALGORITHM = "PBKDF2WithHmacSHA1"

	fun hashPassword(password: String): String {
		val salt = ByteArray(SALT_LENGTH)
		SecureRandom().nextBytes(salt)
		val hash = hashPassword(password, salt, ITERATIONS)
		return "PBKDF2\$$ITERATIONS\$${Base64.encodeToString(salt, Base64.NO_WRAP)}\$${Base64.encodeToString(hash, Base64.NO_WRAP)}"
	}

	fun verifyPassword(password: String, storedHash: String): Boolean {
		if (!storedHash.startsWith("PBKDF2$")) {
			return false
		}
		val parts = storedHash.split("$")
		if (parts.size != 4) return false
		val iterations = parts[1].toInt()
		val salt = Base64.decode(parts[2], Base64.NO_WRAP)
		val hash = Base64.decode(parts[3], Base64.NO_WRAP)
		val testHash = hashPassword(password, salt, iterations)
		return MessageDigest.isEqual(hash, testHash)
	}

	private fun hashPassword(password: String, salt: ByteArray, iterations: Int): ByteArray {
		val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH)
		val factory = SecretKeyFactory.getInstance(ALGORITHM)
		return factory.generateSecret(spec).encoded
	}
}
