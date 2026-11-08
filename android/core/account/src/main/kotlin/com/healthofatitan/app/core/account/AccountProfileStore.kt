package com.healthofatitan.app.core.account

import com.healthofatitan.app.core.security.FieldCipher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * What the account fills in by itself and what the person adds (A65 §2.4, §5; slice А4). Only on
 * the phone (and later in the E2E backup, А6): nothing of it goes to the server or to Claude.
 *
 * | Field | Source | Class |
 * |---|---|---|
 * | [name] | Google `displayName` / WHOOP `first_name` → «Валентин, верно?»; typing wins | S2 |
 * | [googleEmailMask] | Google credential id, kept only as `v•••@gmail.com` | S2 |
 * | [heightCm], [maxPulse] | WHOOP `body_measurement` | S3 |
 * | weight | WHOOP `body_measurement` — **dropped**: no weight goal in the MVP (D-043, D-047 flag off) | — |
 * | avatar | the Google photo once, or the person's own (camera / gallery) | S2 |
 * | «О себе» | typed, ≤ 500 characters, separately sealed | **S4** |
 */
@Serializable
data class AccountProfile(
    val name: String? = null,
    /** [SOURCE_GOOGLE] · [SOURCE_WHOOP] · [SOURCE_MANUAL]. */
    val nameSource: String? = null,
    /** The person said «Да, верно» or typed the name: the next sign-in does not ask again. */
    val nameConfirmed: Boolean = false,
    val googleEmailMask: String? = null,
    val heightCm: Int? = null,
    val maxPulse: Int? = null,
    /** When the body data last came from WHOOP. */
    val bodyAt: Long? = null,
    /** [AVATAR_NONE] (the Titan sign) · [AVATAR_GOOGLE] · [AVATAR_OWN]. */
    val avatar: String = AVATAR_NONE,
    /** CrisisGuard level of the saved «О себе» (0–3): ≥ 1 keeps the quiet «Помощь» line under the field. */
    val aboutLevel: Int = 0,
    /** When «О себе» last wrote a safety event (and maybe opened «Помощь»): once, not on every save. */
    val aboutAlertAt: Long? = null,
) {
    /** «Валентин, верно?»: a name came from Google or WHOOP and the person has not answered yet. */
    val asksName: Boolean get() = name != null && !nameConfirmed

    companion object {
        const val SOURCE_GOOGLE = "google"
        const val SOURCE_WHOOP = "whoop"
        const val SOURCE_MANUAL = "manual"
        const val AVATAR_NONE = "none"
        const val AVATAR_GOOGLE = "google"
        const val AVATAR_OWN = "own"
        const val NAME_MAX = 40
        const val ABOUT_MAX = 500
    }
}

/** Small sealed files of the profile; tests keep them in memory. */
interface ProfileBlobs {
    fun read(name: String): ByteArray?

    fun write(name: String, bytes: ByteArray)

    fun delete(name: String)
}

/** Files in the app's private `filesDir/account_profile/` (no Android backup: `allowBackup=false`). */
class FileProfileBlobs(private val dir: File) : ProfileBlobs {
    override fun read(name: String): ByteArray? = File(dir, name).takeIf { it.isFile }?.readBytes()

    override fun write(name: String, bytes: ByteArray) {
        dir.mkdirs()
        val tmp = File(dir, "$name.tmp")
        tmp.writeBytes(bytes)
        check(tmp.renameTo(File(dir, name))) { "cannot write $name" }
    }

    override fun delete(name: String) {
        File(dir, name).delete()
    }
}

class InMemoryProfileBlobs : ProfileBlobs {
    val files = mutableMapOf<String, ByteArray>()

    override fun read(name: String): ByteArray? = files[name]

    override fun write(name: String, bytes: ByteArray) {
        files[name] = bytes
    }

    override fun delete(name: String) {
        files.remove(name)
    }
}

/**
 * The profile of the account on the phone (A65 §2.4, §5.1, §5.2). Three files, each sealed with
 * Tink AEAD on K_field (Android Keystore) and bound to its place by the associated data:
 * the profile (S2/S3), «О себе» (S4) and the avatar (WebP 512×512, no EXIF). «Очистить» and the
 * deletion of the account remove them; the avatar never goes to an LLM (A65 §5.1).
 */
class AccountProfileStore(
    private val blobs: ProfileBlobs,
    private val cipher: FieldCipher,
    private val json: Json,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val profileState = MutableStateFlow(load())
    private val aboutState = MutableStateFlow(readText(ABOUT, AD_ABOUT).orEmpty())
    private val avatarState = MutableStateFlow(open(AVATAR, AD_AVATAR))

    val profile: StateFlow<AccountProfile> = profileState.asStateFlow()

    /** «О себе» (S4). Empty when not written. */
    val about: StateFlow<String> = aboutState.asStateFlow()

    /** The avatar as WebP bytes, or null: then the Titan sign. */
    val avatar: StateFlow<ByteArray?> = avatarState.asStateFlow()

    private fun open(name: String, ad: ByteArray): ByteArray? = runCatching {
        blobs.read(name)?.let { cipher.decrypt(it, ad) }
    }.getOrNull() // a file that no longer opens (key lost) reads as empty

    private fun readText(name: String, ad: ByteArray): String? = open(name, ad)?.toString(Charsets.UTF_8)

    private fun load(): AccountProfile =
        readText(PROFILE, AD_PROFILE)?.let { runCatching { json.decodeFromString(AccountProfile.serializer(), it) }.getOrNull() }
            ?: AccountProfile()

    private fun seal(name: String, ad: ByteArray, plain: ByteArray) = blobs.write(name, cipher.encrypt(plain, ad))

    @Synchronized
    private fun update(change: (AccountProfile) -> AccountProfile) {
        val next = change(profileState.value)
        if (next == profileState.value) return
        seal(PROFILE, AD_PROFILE, json.encodeToString(AccountProfile.serializer(), next).toByteArray(Charsets.UTF_8))
        profileState.value = next
    }

    /**
     * A name from Google or WHOOP at sign-in (A61 «сначала данные, потом вопрос»): kept as a
     * suggestion «…, верно?»; a name the person typed or confirmed is never replaced.
     */
    fun suggestName(raw: String?, source: String) {
        val name = cleanName(raw) ?: return
        update { p -> if (p.nameSource == AccountProfile.SOURCE_MANUAL || (p.nameConfirmed && p.name != null)) p else p.copy(name = name, nameSource = source, nameConfirmed = false) }
    }

    /** «Да, верно». */
    fun confirmName() = update { it.copy(nameConfirmed = it.name != null) }

    /** The name typed by hand wins over every source; blank removes it. */
    fun setName(raw: String) {
        val name = cleanName(raw)
        update { it.copy(name = name, nameSource = name?.let { AccountProfile.SOURCE_MANUAL }, nameConfirmed = name != null) }
    }

    /** The e-mail of the Google account, masked at once: only `v•••@gmail.com` is kept (A65 §2.4). */
    fun setGoogleEmail(email: String?) {
        val mask = maskEmail(email) ?: return
        update { it.copy(googleEmailMask = mask) }
    }

    /** Height and max pulse from WHOOP (A65 §2.4). Implausible values are dropped, not stored. */
    fun setBody(heightCm: Int?, maxPulse: Int?) {
        val h = heightCm?.takeIf { it in 100..250 }
        val p = maxPulse?.takeIf { it in 100..230 }
        if (h == null && p == null) return
        update { it.copy(heightCm = h ?: it.heightCm, maxPulse = p ?: it.maxPulse, bodyAt = now()) }
    }

    /** «О себе», S4: at most [AccountProfile.ABOUT_MAX] characters; blank deletes the file. */
    @Synchronized
    fun setAbout(text: String) {
        val value = text.trim().take(AccountProfile.ABOUT_MAX)
        if (value.isEmpty()) {
            blobs.delete(ABOUT)
        } else {
            seal(ABOUT, AD_ABOUT, value.toByteArray(Charsets.UTF_8))
        }
        aboutState.value = value
    }

    /** The CrisisGuard level of the saved «О себе» and the time of its last safety event (A65 §5.2). */
    fun setAboutLevel(level: Int, alertAt: Long?) = update { it.copy(aboutLevel = level.coerceIn(0, 3), aboutAlertAt = alertAt) }

    /** The person's own photo (already cropped, scaled and re-encoded without EXIF). */
    @Synchronized
    fun setOwnAvatar(webp: ByteArray) {
        seal(AVATAR, AD_AVATAR, webp)
        avatarState.value = webp
        update { it.copy(avatar = AccountProfile.AVATAR_OWN) }
    }

    /** The Google photo, downloaded once at sign-in: only while the person has no own photo. */
    @Synchronized
    fun setGoogleAvatar(webp: ByteArray) {
        if (profileState.value.avatar == AccountProfile.AVATAR_OWN) return
        seal(AVATAR, AD_AVATAR, webp)
        avatarState.value = webp
        update { it.copy(avatar = AccountProfile.AVATAR_GOOGLE) }
    }

    /** «Удалить фото»: the Titan sign again. */
    @Synchronized
    fun deleteAvatar() {
        blobs.delete(AVATAR)
        avatarState.value = null
        update { it.copy(avatar = AccountProfile.AVATAR_NONE) }
    }

    /** Everything of the profile (sign-out with a wipe, deletion of the account). */
    @Synchronized
    fun clear() {
        listOf(PROFILE, ABOUT, AVATAR).forEach(blobs::delete)
        profileState.value = AccountProfile()
        aboutState.value = ""
        avatarState.value = null
    }

    companion object {
        const val DIR = "account_profile"
        private const val PROFILE = "profile.bin"
        private const val ABOUT = "about.bin"
        private const val AVATAR = "avatar.bin"
        private val AD_PROFILE = "S2.account_profile.v1".toByteArray()
        private val AD_ABOUT = "S4.about.v1".toByteArray()
        private val AD_AVATAR = "S2.avatar.v1".toByteArray()

        fun cleanName(raw: String?): String? =
            raw?.replace(Regex("[\\p{Cntrl}]"), "")?.trim()?.take(AccountProfile.NAME_MAX)?.takeIf { it.isNotEmpty() }

        /** `valentin@gmail.com` → `v•••@gmail.com`; anything without «@» is not kept. */
        fun maskEmail(email: String?): String? {
            val e = email?.trim() ?: return null
            val at = e.lastIndexOf('@')
            if (at < 1 || at == e.length - 1) return null
            return "${e.first()}•••${e.substring(at)}"
        }
    }
}
