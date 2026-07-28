package com.appause.android.data.pro

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PublicKey

/**
 * DeviceKeyStore — creates and returns a stable, device-unique RSA key pair
 * backed by the Android Keystore (hardware-backed where available).
 *
 * Why a device key?
 * - Appause Pro license tokens are DEVICE-BOUND: the server embeds this
 *   device's public-key fingerprint in the JWT "device" claim. During local
 *   verification we compare that claim to the fingerprint computed here, so a
 *   token copied from another phone cannot unlock Pro on this one.
 *
 * Security notes:
 * - The private key is non-extractable (Android Keystore) and never leaves the
 *   device. We only ever read the PUBLIC key out, to send its fingerprint to
 *   the server at activation time.
 * - We include PURPOSE_SIGN for maximum Keystore compatibility, but the app
 *   never signs anything with this key — it exists only to expose a stable
 *   public key for binding.
 */
object DeviceKeyStore {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "appause_device_key"

    /** Returns the device's RSA public key, creating the keypair on first use. */
    fun getPublicKey(context: Context): PublicKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!keyStore.containsAlias(ALIAS)) {
            val generator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_RSA,
                ANDROID_KEYSTORE
            )
            val spec = KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            )
                .setKeySize(2048)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                .build()
            generator.initialize(spec)
            generator.generateKeyPair()
        }
        return keyStore.getCertificate(ALIAS).publicKey
    }

    /**
     * Stable, device-unique fingerprint derived from the public key.
     * SHA-256 of the DER-encoded public key, returned as a lowercase hex string.
     * This is what the server puts in the JWT "device" claim.
     */
    fun getDeviceFingerprint(context: Context): String {
        val publicKeyBytes = getPublicKey(context).encoded
        val digest = MessageDigest.getInstance("SHA-256").digest(publicKeyBytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
