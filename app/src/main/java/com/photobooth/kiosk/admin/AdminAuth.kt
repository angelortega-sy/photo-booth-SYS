package com.photobooth.kiosk.admin

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Stores a salted hash of the admin PIN. The PIN itself is never persisted.
 */
class AdminAuth(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    val hasPin: Boolean
        get() = prefs.contains(KEY_SALT) && prefs.contains(KEY_HASH)

    fun setPin(pin: String) {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY_SALT, salt.toHex())
            .putString(KEY_HASH, hash(pin, salt).toHex())
            .apply()
    }

    fun verify(pin: String): Boolean = runCatching {
        val salt = prefs.getString(KEY_SALT, null)?.hexToBytes() ?: return false
        val expected = prefs.getString(KEY_HASH, null)?.hexToBytes() ?: return false
        MessageDigest.isEqual(hash(pin, salt), expected)
    }.getOrDefault(false)

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun hash(pin: String, salt: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        return digest.digest(pin.toByteArray(Charsets.UTF_8))
    }

    private fun ByteArray.toHex(): String =
        joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xFF) }

    private fun String.hexToBytes(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private companion object {
        const val PREFS_NAME = "admin_auth"
        const val KEY_SALT = "pin_salt"
        const val KEY_HASH = "pin_hash"
        const val SALT_BYTES = 16
    }
}
