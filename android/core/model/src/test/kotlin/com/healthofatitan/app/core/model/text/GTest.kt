package com.healthofatitan.app.core.model.text

import com.healthofatitan.app.core.model.profile.AddressForm
import com.healthofatitan.app.core.model.profile.ProfileSettings
import com.healthofatitan.app.core.model.profile.SexForCalc
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GTest {

    private val line = G(n = "Отмечено", m = "Ты отметил", f = "Ты отметила")

    @Test
    fun `each address form picks its own text`() {
        assertEquals("Отмечено", line.of(AddressForm.NEUTRAL))
        assertEquals("Ты отметил", line.of(AddressForm.MASCULINE))
        assertEquals("Ты отметила", line.of(AddressForm.FEMININE))
    }

    @Test
    fun `missing forms fall back to the neutral one`() {
        val neutral = G("Сделано")
        AddressForm.entries.forEach { assertEquals("Сделано", neutral.of(it)) }
        assertEquals("Сон долгий", G("Сон долгий", m = "Выспался").of(AddressForm.FEMININE))
    }

    @Test
    fun `not chosen means neutral, unknown codes too`() {
        assertEquals(AddressForm.NEUTRAL, ProfileSettings().addressForm)
        assertEquals(AddressForm.NEUTRAL, AddressForm.fromCode(null))
        assertEquals(AddressForm.NEUTRAL, AddressForm.fromCode("x"))
        AddressForm.entries.forEach { assertEquals(it, AddressForm.fromCode(it.code)) }
        assertEquals(listOf("n", "m", "f"), AddressForm.entries.map { it.code })
    }

    @Test
    fun `sex for calculations is a separate field, unspecified by default`() {
        assertEquals(SexForCalc.UNSPECIFIED, ProfileSettings(addressForm = AddressForm.FEMININE).sexForCalc)
        assertEquals(SexForCalc.UNSPECIFIED, SexForCalc.fromCode(null))
        SexForCalc.entries.forEach { assertEquals(it, SexForCalc.fromCode(it.code)) }
    }

    @Test
    fun `age comes from the year of birth for adults only`() {
        assertEquals(38, ProfileSettings(birthYear = 1988).ageIn(2026))
        assertNull(ProfileSettings().ageIn(2026))
        assertNull(ProfileSettings(birthYear = 2015).ageIn(2026))
        assertTrue(ProfileSettings.validBirthYear(1988, 2026))
        assertTrue(ProfileSettings.validBirthYear(2008, 2026))
        assertFalse(ProfileSettings.validBirthYear(2009, 2026))
        assertFalse(ProfileSettings.validBirthYear(1900, 2026))
        assertFalse(ProfileSettings.validBirthYear(null, 2026))
    }
}
