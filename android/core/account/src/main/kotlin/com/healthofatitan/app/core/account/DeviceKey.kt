package com.healthofatitan.app.core.account

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

/**
 * The phone's own key (A65 §3.3): EC P-256 that never leaves the device. Its public part goes to
 * the Worker at sign-in; every refresh is signed with it, so a copied refresh token is useless on
 * another device.
 */
interface DeviceKey {
    /** SPKI of the public key, base64url without padding. Creates the key on first use. */
    fun publicKey(): String

    /** `SHA256withECDSA` over the UTF-8 bytes of [message]: ASN.1 DER, base64url. */
    fun sign(message: String): String

    /** «Выйти»: the key is deleted; the next sign-in makes a new one. */
    fun reset()
}

internal val b64url: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()

/** `hot-login|v1|<nonce or state or ticket>` — what the key signs at sign-in. */
fun loginMessage(binding: String): String = "hot-login|v1|$binding"

/** `hot-refresh|v1|<base64url(sha256(refresh))>|<ts>` — what the key signs at refresh. */
fun refreshMessage(refresh: String, ts: Long): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(refresh.toByteArray(Charsets.UTF_8))
    return "hot-refresh|v1|${b64url.encodeToString(digest)}|$ts"
}

/**
 * Android Keystore key, StrongBox when the phone has it (Pixel 9 Pro XL does), not exportable.
 * No user authentication is required: refresh runs in background sync too.
 */
class KeystoreDeviceKey(private val alias: String = ALIAS) : DeviceKey {

    private fun keyStore(): KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

    @Synchronized
    private fun keyPair(): Pair<PrivateKey, PublicKey> {
        val ks = keyStore()
        val existing = ks.getEntry(alias, null) as? KeyStore.PrivateKeyEntry
        if (existing != null) return existing.privateKey to existing.certificate.publicKey
        val pair = try {
            generate(strongBox = true)
        } catch (_: StrongBoxUnavailableException) {
            generate(strongBox = false)
        }
        return pair.private to pair.public
    }

    private fun generate(strongBox: Boolean): KeyPair {
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setIsStrongBoxBacked(strongBox)
            .build()
        return KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, PROVIDER).run {
            initialize(spec)
            generateKeyPair()
        }
    }

    override fun publicKey(): String = b64url.encodeToString(keyPair().second.encoded)

    override fun sign(message: String): String {
        val signature = Signature.getInstance("SHA256withECDSA").apply {
            initSign(keyPair().first)
            update(message.toByteArray(Charsets.UTF_8))
        }
        return b64url.encodeToString(signature.sign())
    }

    @Synchronized
    override fun reset() {
        runCatching { keyStore().deleteEntry(alias) }
    }

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val ALIAS = "hot_device_key_v1"
    }
}

/** A software key with the same algorithm: tests and previews only. */
class SoftwareDeviceKey : DeviceKey {
    private var pair: KeyPair = newPair()

    private fun newPair(): KeyPair = KeyPairGenerator.getInstance("EC").run {
        initialize(ECGenParameterSpec("secp256r1"))
        generateKeyPair()
    }

    override fun publicKey(): String = b64url.encodeToString(pair.public.encoded)

    override fun sign(message: String): String = b64url.encodeToString(
        Signature.getInstance("SHA256withECDSA").run {
            initSign(pair.private)
            update(message.toByteArray(Charsets.UTF_8))
            sign()
        },
    )

    fun verify(message: String, signature: String): Boolean = Signature.getInstance("SHA256withECDSA").run {
        initVerify(pair.public)
        update(message.toByteArray(Charsets.UTF_8))
        verify(Base64.getUrlDecoder().decode(signature))
    }

    override fun reset() {
        pair = newPair()
    }
}
