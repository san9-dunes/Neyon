package io.github.landwarderer.neyon.core.util

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object HashUtils {
    private const val ITERATIONS = 100000
    private const val KEY_LENGTH = 160
    private const val SALT_LENGTH = 16

    fun hashPassword(password: String): String {
        val salt = ByteArray(SALT_LENGTH)
        SecureRandom().nextBytes(salt)
        return hashWithSalt(password, salt, ITERATIONS)
    }

    private fun hashWithSalt(password: String, salt: ByteArray, iterations: Int): String {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        val hash = factory.generateSecret(spec).encoded

        val saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP)
        val hashBase64 = Base64.encodeToString(hash, Base64.NO_WRAP)

        return "PBKDF2\$$iterations\$$saltBase64\$$hashBase64"
    }

    fun verifyPassword(password: String, storedHash: String): Boolean {
        if (!storedHash.startsWith("PBKDF2$")) return false

        try {
            val parts = storedHash.split("$")
            if (parts.size != 4) return false

            val iterations = parts[1].toInt()
            val salt = Base64.decode(parts[2], Base64.NO_WRAP)
            val computedHash = hashWithSalt(password, salt, iterations)

            return computedHash == storedHash
        } catch (e: Exception) {
            return false
        }
    }
}
