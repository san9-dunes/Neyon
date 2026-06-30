package io.github.landwarderer.neyon.core.util

import java.security.SecureRandom
import java.security.spec.KeySpec
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import android.util.Base64

object HashUtils {
    private const val ITERATIONS = 100_000
    private const val KEY_LENGTH = 160
    private const val ALGORITHM = "PBKDF2WithHmacSHA1"
    private const val SALT_LENGTH = 16

    fun hashPassword(password: String): String {
        val salt = ByteArray(SALT_LENGTH)
        SecureRandom().nextBytes(salt)
        return hashPassword(password, salt)
    }

    fun verifyPassword(password: String, storedHash: String): Boolean {
        if (!storedHash.startsWith("PBKDF2$")) {
            return false
        }
        val parts = storedHash.split("$")
        if (parts.size != 4) return false

        val iterations = parts[1].toInt()
        val salt = Base64.decode(parts[2], Base64.NO_WRAP)
        val hash = parts[3]

        val calculatedHashParts = hashPassword(password, salt, iterations).split("$")
        return calculatedHashParts.size == 4 && calculatedHashParts[3] == hash
    }

    private fun hashPassword(password: String, salt: ByteArray, iterations: Int = ITERATIONS): String {
        val spec: KeySpec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance(ALGORITHM)
        val hash = factory.generateSecret(spec).encoded

        val saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP)
        val hashBase64 = Base64.encodeToString(hash, Base64.NO_WRAP)

        return "PBKDF2$$iterations$$saltBase64$$hashBase64"
    }
}
