package io.github.landwarderer.neyon.core.util

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object HashUtils {
    private const val ITERATIONS = 100_000
    private const val KEY_LENGTH = 160 // bits
    private const val SALT_LENGTH = 16 // bytes
    private const val ALGORITHM = "PBKDF2WithHmacSHA1"

    fun hashPassword(password: String): String {
        val salt = ByteArray(SALT_LENGTH)
        SecureRandom().nextBytes(salt)

        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance(ALGORITHM)
        val hash = factory.generateSecret(spec).encoded

        val saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP)
        val hashBase64 = Base64.encodeToString(hash, Base64.NO_WRAP)

        return "PBKDF2\$$ITERATIONS\$$saltBase64\$$hashBase64"
    }

    fun verifyPassword(password: String, storedHash: String): Boolean {
        try {
            val parts = storedHash.split("$")
            if (parts.size != 4 || parts[0] != "PBKDF2") {
                return false
            }

            val iterations = parts[1].toInt()
            val salt = Base64.decode(parts[2], Base64.NO_WRAP)
            val expectedHashBase64 = parts[3]

            val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH)
            val factory = SecretKeyFactory.getInstance(ALGORITHM)
            val computedHash = factory.generateSecret(spec).encoded
            val computedHashBase64 = Base64.encodeToString(computedHash, Base64.NO_WRAP)

            // Constant-time comparison
            var diff = 0
            val a = expectedHashBase64.toByteArray()
            val b = computedHashBase64.toByteArray()
            if (a.size != b.size) return false
            for (i in a.indices) {
                diff = diff or (a[i].toInt() xor b[i].toInt())
            }
            return diff == 0
        } catch (e: Exception) {
            return false
        }
    }
}
