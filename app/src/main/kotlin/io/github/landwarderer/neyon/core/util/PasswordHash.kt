package io.github.landwarderer.neyon.core.util

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PasswordHash {
    private const val ITERATIONS = 10000
    private const val KEY_LENGTH = 256
    private const val SALT_LENGTH = 16
    private const val ALGORITHM = "PBKDF2WithHmacSHA1"

    fun hash(password: String): String {
        val salt = ByteArray(SALT_LENGTH)
        SecureRandom().nextBytes(salt)
        val hash = hash(password, salt)
        val saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP)
        val hashBase64 = Base64.encodeToString(hash, Base64.NO_WRAP)
        return "$saltBase64:$hashBase64"
    }

    fun verify(password: String, storedHash: String): Boolean {
        val parts = storedHash.split(":")
        if (parts.size != 2) return false
        return try {
            val salt = Base64.decode(parts[0], Base64.NO_WRAP)
            val hash = Base64.decode(parts[1], Base64.NO_WRAP)
            val testHash = hash(password, salt)
            java.security.MessageDigest.isEqual(hash, testHash)
        } catch (e: Exception) {
            false
        }
    }

    private fun hash(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance(ALGORITHM)
        return factory.generateSecret(spec).encoded
    }
}
