package com.photobooth.kiosk.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * EC P-256 key that never leaves the Android Keystore. The private key is
 * not exportable; callers only receive the SPKI PEM and ECDSA signatures.
 */
class DeviceKeyStore {

    fun publicKeyPem(): String {
        ensureKey()
        val encoded = certificate().publicKey.encoded
        val body = Base64.encodeToString(encoded, Base64.NO_WRAP)
            .chunked(64)
            .joinToString("\n")
        return "-----BEGIN PUBLIC KEY-----\n$body\n-----END PUBLIC KEY-----\n"
    }

    fun sign(payload: ByteArray): String {
        ensureKey()
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(privateKey())
        signature.update(payload)
        return Base64.encodeToString(signature.sign(), Base64.NO_WRAP)
    }

    private fun ensureKey() {
        val store = keyStore()
        if (store.containsAlias(ALIAS)) return
        val generator = java.security.KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            ANDROID_KEYSTORE,
        )
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setUserAuthenticationRequired(false)
            .build()
        generator.initialize(spec)
        generator.generateKeyPair()
    }

    private fun privateKey(): java.security.PrivateKey {
        val entry = keyStore().getEntry(ALIAS, null) as KeyStore.PrivateKeyEntry
        return entry.privateKey
    }

    private fun certificate(): java.security.cert.Certificate = keyStore().getCertificate(ALIAS)

    private fun keyStore(): KeyStore =
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "photobooth-device"
    }
}
