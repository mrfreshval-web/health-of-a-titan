package com.healthofatitan.app.core.account

import android.graphics.ImageDecoder
import com.healthofatitan.app.core.network.await
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.nio.ByteBuffer

/**
 * After «Войти через Google» or «Привязать Google» (A65 §2.4): the name becomes a suggestion
 * «…, верно?», the e-mail is kept only as a mask, the photo is downloaded once and becomes the
 * avatar unless the person has their own. All of it stays on the phone; the server never sees it.
 */
class GoogleProfileImport(
    private val profile: AccountProfileStore,
    private val http: OkHttpClient,
    /** Tests replace the decoding (no real image codecs on the JVM). */
    private val toAvatar: (ByteArray) -> ByteArray? = { AvatarImage.square(ImageDecoder.createSource(ByteBuffer.wrap(it))) },
) {
    suspend fun import(google: GoogleIdResult.Ok) {
        profile.suggestName(google.displayName, AccountProfile.SOURCE_GOOGLE)
        profile.setGoogleEmail(google.email)
        if (profile.profile.value.avatar != AccountProfile.AVATAR_NONE) return
        val url = pictureUrl(google.pictureUri) ?: return
        val bytes = runCatching { download(url) }.getOrNull() ?: return
        toAvatar(bytes)?.let(profile::setGoogleAvatar)
    }

    private suspend fun download(url: String): ByteArray? {
        val request = Request.Builder().url(url).get().build()
        http.newCall(request).await().use { res ->
            if (!res.isSuccessful) return null
            val length = res.body.contentLength()
            if (length > MAX_BYTES) return null
            return res.body.bytes().takeIf { it.size <= MAX_BYTES }
        }
    }

    companion object {
        private const val MAX_BYTES = 4L * 1024 * 1024

        /** Only https; the Google size suffix `=s96-c` asks for 512 px instead. */
        fun pictureUrl(raw: String?): String? {
            val url = raw?.toHttpUrlOrNull() ?: return null
            if (!url.isHttps) return null
            val text = url.toString()
            return Regex("=s\\d+(-c)?$").find(text)?.let { text.replaceRange(it.range, "=s512-c") } ?: text
        }
    }
}
