package com.healthofatitan.app.core.account

import com.healthofatitan.app.core.network.HotJson
import com.healthofatitan.app.core.security.FieldCipher
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Test
import java.security.GeneralSecurityException
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The account part of the profile on the phone (A65 §2.4, slice А4): sealed per field class, the
 * name rules «…, верно?», the masked e-mail, WHOOP body data in range, «О себе» ≤ 500, the avatar.
 */
class AccountProfileStoreTest {

    /** XOR «cipher» that checks the associated data, like an AEAD would. */
    private class FakeCipher : FieldCipher {
        override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray =
            associatedData.size.toByte().let { byteArrayOf(it) } + associatedData + plaintext.map { (it.toInt() xor 0x5A).toByte() }

        override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray): ByteArray {
            val n = ciphertext[0].toInt()
            if (!ciphertext.copyOfRange(1, 1 + n).contentEquals(associatedData)) throw GeneralSecurityException("ad")
            return ciphertext.copyOfRange(1 + n, ciphertext.size).map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
        }
    }

    private val blobs = InMemoryProfileBlobs()
    private val cipher = FakeCipher()

    private fun store() = AccountProfileStore(blobs, cipher, HotJson, now = { 42L })

    @Test
    fun `everything survives a restart and nothing is stored in the clear`() {
        val s = store()
        s.setName("Валентин")
        s.setAbout("Работаю посменно, сплю днём.")
        s.setOwnAvatar(byteArrayOf(1, 2, 3))
        val again = store()
        assertEquals("Валентин", again.profile.value.name)
        assertEquals("Работаю посменно, сплю днём.", again.about.value)
        assertContentEquals(byteArrayOf(1, 2, 3), again.avatar.value)
        assertEquals(AccountProfile.AVATAR_OWN, again.profile.value.avatar)
        listOf("profile.bin", "about.bin").forEach { name ->
            val raw = String(requireNotNull(blobs.read(name)), Charsets.UTF_8)
            assertFalse("Валентин" in raw || "посменно" in raw, "$name is sealed")
        }
    }

    @Test
    fun `a suggested name asks once and never replaces a typed one`() {
        val s = store()
        s.suggestName("Валентин", AccountProfile.SOURCE_GOOGLE)
        assertTrue(s.profile.value.asksName)
        s.confirmName()
        assertFalse(s.profile.value.asksName)
        s.suggestName("Val", AccountProfile.SOURCE_WHOOP)
        assertEquals("Валентин", s.profile.value.name, "a confirmed name stays")
        s.setName("  Валя ")
        s.suggestName("Валентин", AccountProfile.SOURCE_GOOGLE)
        assertEquals("Валя", s.profile.value.name)
        assertEquals(AccountProfile.SOURCE_MANUAL, s.profile.value.nameSource)
    }

    @Test
    fun `only the masked e-mail is kept`() {
        val s = store()
        s.setGoogleEmail("valentin@gmail.com")
        assertEquals("v•••@gmail.com", s.profile.value.googleEmailMask)
        assertNull(AccountProfileStore.maskEmail("no-at-sign"))
        assertNull(AccountProfileStore.maskEmail("@gmail.com"))
        assertFalse("valentin" in String(requireNotNull(blobs.read("profile.bin")), Charsets.UTF_8))
    }

    @Test
    fun `body data out of range is dropped`() {
        val s = store()
        s.setBody(heightCm = 182, maxPulse = 400)
        assertEquals(182, s.profile.value.heightCm)
        assertNull(s.profile.value.maxPulse)
        assertEquals(42L, s.profile.value.bodyAt)
        s.setBody(heightCm = null, maxPulse = 190)
        assertEquals(182, s.profile.value.heightCm)
        assertEquals(190, s.profile.value.maxPulse)
    }

    @Test
    fun `about is cut at 500 and blank deletes the file`() {
        val s = store()
        s.setAbout("я".repeat(600))
        assertEquals(AccountProfile.ABOUT_MAX, s.about.value.length)
        s.setAbout("   ")
        assertEquals("", s.about.value)
        assertNull(blobs.read("about.bin"))
    }

    @Test
    fun `the Google photo never replaces an own one and clear removes everything`() {
        val s = store()
        s.setGoogleAvatar(byteArrayOf(7))
        assertEquals(AccountProfile.AVATAR_GOOGLE, s.profile.value.avatar)
        s.setOwnAvatar(byteArrayOf(9))
        s.setGoogleAvatar(byteArrayOf(7))
        assertContentEquals(byteArrayOf(9), s.avatar.value)
        s.deleteAvatar()
        assertNull(s.avatar.value)
        assertEquals(AccountProfile.AVATAR_NONE, s.profile.value.avatar)
        s.setName("Валя")
        s.clear()
        assertEquals(AccountProfile(), store().profile.value)
        assertNull(blobs.read("profile.bin"))
    }

    @Test
    fun `the Google picture is https only and asked at 512 px`() {
        assertEquals("https://lh3.googleusercontent.com/a/abc=s512-c", GoogleProfileImport.pictureUrl("https://lh3.googleusercontent.com/a/abc=s96-c"))
        assertEquals("https://lh3.googleusercontent.com/a/abc", GoogleProfileImport.pictureUrl("https://lh3.googleusercontent.com/a/abc"))
        assertNull(GoogleProfileImport.pictureUrl("http://lh3.googleusercontent.com/a/abc=s96-c"))
        assertNull(GoogleProfileImport.pictureUrl("not a url"))
        assertNull(GoogleProfileImport.pictureUrl(null))
    }

    @Test
    fun `a Google import suggests the name and keeps only the masked e-mail`() = runBlocking {
        val s = store()
        GoogleProfileImport(s, OkHttpClient(), toAvatar = { it }).import(GoogleIdResult.Ok("id", "Валентин", pictureUri = null, email = "valentin@gmail.com"))
        assertEquals("Валентин", s.profile.value.name)
        assertTrue(s.profile.value.asksName)
        assertEquals("v•••@gmail.com", s.profile.value.googleEmailMask)
        assertNull(s.avatar.value, "no picture: the Titan sign stays")
    }

    @Test
    fun `a portrait avatar is cut higher than the centre, a landscape one in the middle`() {
        assertEquals(0 to 100, AvatarImage.origin(300, 600))
        assertEquals(150 to 0, AvatarImage.origin(600, 300))
        assertEquals(0 to 0, AvatarImage.origin(400, 400))
    }

    @Test
    fun `the CrisisGuard level of about is kept between 0 and 3`() {
        val s = store()
        s.setAboutLevel(5, 7L)
        assertEquals(3, s.profile.value.aboutLevel)
        assertEquals(7L, store().profile.value.aboutAlertAt)
    }
}
