package com.healthofatitan.app.core.account

import android.content.Context
import com.healthofatitan.app.core.security.FieldCipher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.util.Base64

interface AccountCredentialStore {
    val credentials: StateFlow<AccountCredentials?>

    fun save(credentials: AccountCredentials)

    /** «Выйти», a revoked session, «Удалить всё»: the account is gone from the phone. */
    fun clear()
}

/**
 * The refresh token at rest (A65 §3.1): Tink AEAD with the Keystore-backed K_field, in a private
 * SharedPreferences file excluded from backups (`allowBackup=false`) — like the WHOOP session
 * store. The access token is never written anywhere.
 */
class EncryptedAccountCredentialStore(
    context: Context,
    private val cipher: FieldCipher,
    private val json: Json,
) : AccountCredentialStore {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(load())
    override val credentials: StateFlow<AccountCredentials?> = state.asStateFlow()

    private fun load(): AccountCredentials? = runCatching {
        val sealed = prefs.getString(KEY, null) ?: return null
        val plain = String(cipher.decrypt(Base64.getDecoder().decode(sealed), AD), Charsets.UTF_8)
        json.decodeFromString(AccountCredentials.serializer(), plain)
    }.getOrNull() // a value that no longer decrypts (key lost) counts as «signed out»

    @Synchronized
    override fun save(credentials: AccountCredentials) {
        val plain = json.encodeToString(AccountCredentials.serializer(), credentials).toByteArray(Charsets.UTF_8)
        prefs.edit().putString(KEY, Base64.getEncoder().encodeToString(cipher.encrypt(plain, AD))).commit()
        state.value = credentials
    }

    @Synchronized
    override fun clear() {
        prefs.edit().clear().commit()
        state.value = null
    }

    private companion object {
        const val PREFS = "hot_account_sec"
        const val KEY = "account"
        val AD = "SEC.account.v1".toByteArray()
    }
}

/** For tests and previews. */
class InMemoryAccountCredentialStore(initial: AccountCredentials? = null) : AccountCredentialStore {
    private val state = MutableStateFlow(initial)
    override val credentials: StateFlow<AccountCredentials?> = state.asStateFlow()

    override fun save(credentials: AccountCredentials) {
        state.value = credentials
    }

    override fun clear() {
        state.value = null
    }
}
