package com.healthofatitan.app.core.security

import com.google.crypto.tink.Aead

/**
 * K_field (A14 §3.1, ADR-13): AEAD for S4 free text and SEC values, on top of SQLCipher.
 * [associatedData] binds a ciphertext to its place (e.g. "Transcript.textEnc:<id>") so that a
 * value cannot be moved to another row or column.
 */
interface FieldCipher {
    fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray
    fun decrypt(ciphertext: ByteArray, associatedData: ByteArray): ByteArray
}

class TinkFieldCipher(private val aead: Aead) : FieldCipher {
    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray =
        aead.encrypt(plaintext, associatedData)

    override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray): ByteArray =
        aead.decrypt(ciphertext, associatedData)
}
