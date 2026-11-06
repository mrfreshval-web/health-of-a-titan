package com.healthofatitan.app.core.security

import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import org.junit.Test
import java.security.GeneralSecurityException
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/** A14 §9 step 4: JVM roundtrip of the field AEAD (in-memory keyset, no Keystore). */
class TinkFieldCipherTest {

    private fun cipher(): FieldCipher {
        AeadConfig.register()
        val handle = KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM"))
        return TinkFieldCipher(handle.getPrimitive(RegistryConfiguration.get(), Aead::class.java))
    }

    @Test
    fun `roundtrip restores plaintext`() {
        val c = cipher()
        val plain = "Разговор с наставником".toByteArray()
        val ad = "Transcript.textEnc:1".toByteArray()
        val enc = c.encrypt(plain, ad)
        assertFalse(enc.contentEquals(plain))
        assertContentEquals(plain, c.decrypt(enc, ad))
    }

    @Test
    fun `ciphertext bound to associated data`() {
        val c = cipher()
        val enc = c.encrypt("x".toByteArray(), "row:1".toByteArray())
        assertFailsWith<GeneralSecurityException> { c.decrypt(enc, "row:2".toByteArray()) }
    }
}
