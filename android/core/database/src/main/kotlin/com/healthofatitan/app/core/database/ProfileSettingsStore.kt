package com.healthofatitan.app.core.database

import com.healthofatitan.app.core.database.dao.PersonProfileDao
import com.healthofatitan.app.core.database.entity.PersonProfileEntity
import com.healthofatitan.app.core.model.profile.AddressForm
import com.healthofatitan.app.core.model.profile.ProfileSettings
import com.healthofatitan.app.core.model.profile.SexForCalc
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Read and write the profile «Я» (A61 §2.1, D-055): local-first, in the encrypted database, never
 * on a server (there is no account in the MVP). An empty table reads as [ProfileSettings] defaults:
 * neutral address, no year of birth, sex for calculations not given.
 */
class ProfileSettingsStore(
    private val dao: PersonProfileDao,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val user = HotDatabase.LOCAL_USER

    val settings: Flow<ProfileSettings> = dao.observe(user).map(::decode).distinctUntilChanged()

    val addressForm: Flow<AddressForm> = settings.map { it.addressForm }.distinctUntilChanged()

    suspend fun current(): ProfileSettings = settings.first()

    suspend fun setAddressForm(form: AddressForm) = update { it.copy(addressForm = form) }

    /** null clears the year; a year outside [ProfileSettings.validBirthYear] is the caller's to refuse. */
    suspend fun setBirthYear(year: Int?) = update { it.copy(birthYear = year) }

    suspend fun setSexForCalc(sex: SexForCalc) = update { it.copy(sexForCalc = sex) }

    private suspend fun update(change: (ProfileSettings) -> ProfileSettings) {
        val next = change(decode(dao.get(user)))
        dao.upsert(PersonProfileEntity(user, next.addressForm.code, next.birthYear, next.sexForCalc.code, now()))
    }

    companion object {
        fun decode(row: PersonProfileEntity?): ProfileSettings = row?.let {
            ProfileSettings(
                addressForm = AddressForm.fromCode(it.addressForm),
                birthYear = it.birthYear,
                sexForCalc = SexForCalc.fromCode(it.sexForCalc),
            )
        } ?: ProfileSettings()
    }
}
