package com.healthofatitan.app.integration.whoop

import android.content.Context
import com.healthofatitan.app.core.security.FieldCipher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Base64

/** The phone's WHOOP credential: only the device session of the Worker (class SEC, A14 §3.1). */
data class WhoopCredentials(val session: String, val whoopUserId: String)

interface WhoopCredentialStore {
    val credentials: StateFlow<WhoopCredentials?>

    fun save(credentials: WhoopCredentials)

    /** «Отключить» and «Удалить всё»: the session is gone from the phone. */
    fun clear()
}

/**
 * Session at rest: Tink AEAD with the Keystore-backed K_field (A14 §3.1 SEC, ADR-13), in a
 * private SharedPreferences file that is excluded from backups (`allowBackup=false`). The
 * WHOOP refresh token is not here at all — it lives only in the Worker's Durable Object (D-025);
 * the access token stays in memory.
 */
class EncryptedWhoopCredentialStore(
    context: Context,
    private val cipher: FieldCipher,
) : WhoopCredentialStore {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(load())
    override val credentials: StateFlow<WhoopCredentials?> = state.asStateFlow()

    private fun load(): WhoopCredentials? {
        val session = read(KEY_SESSION) ?: return null
        val user = read(KEY_USER) ?: return null
        return WhoopCredentials(session, user)
    }

    private fun read(key: String): String? = runCatching {
        val sealed = prefs.getString(key, null) ?: return null
        String(cipher.decrypt(Base64.getDecoder().decode(sealed), ad(key)), Charsets.UTF_8)
    }.getOrNull() // a value that no longer decrypts (key lost) counts as «not connected»

    private fun ad(key: String) = "SEC.whoop.$key".toByteArray()

    private fun seal(key: String, value: String): String =
        Base64.getEncoder().encodeToString(cipher.encrypt(value.toByteArray(Charsets.UTF_8), ad(key)))

    @Synchronized
    override fun save(credentials: WhoopCredentials) {
        prefs.edit()
            .putString(KEY_SESSION, seal(KEY_SESSION, credentials.session))
            .putString(KEY_USER, seal(KEY_USER, credentials.whoopUserId))
            .commit()
        state.value = credentials
    }

    @Synchronized
    override fun clear() {
        prefs.edit().clear().commit()
        state.value = null
    }

    private companion object {
        const val PREFS = "hot_whoop_sec"
        const val KEY_SESSION = "session"
        const val KEY_USER = "whoop_user"
    }
}

/** For tests and previews. */
class InMemoryWhoopCredentialStore(initial: WhoopCredentials? = null) : WhoopCredentialStore {
    private val state = MutableStateFlow(initial)
    override val credentials: StateFlow<WhoopCredentials?> = state.asStateFlow()

    override fun save(credentials: WhoopCredentials) {
        state.value = credentials
    }

    override fun clear() {
        state.value = null
    }
}
