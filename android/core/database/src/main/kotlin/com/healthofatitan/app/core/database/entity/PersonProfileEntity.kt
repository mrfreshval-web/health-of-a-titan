package com.healthofatitan.app.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The profile «Я» (A61 §2.1, D-055): the three facts the person types by hand. One row per user.
 * In the encrypted database because the sex for calculations is health data (S3, A14 §3).
 *
 * @property addressForm `n | m | f` (`AddressForm.code`); `n` = not chosen
 * @property sexForCalc `unspecified | female | male` (`SexForCalc.code`)
 */
@Entity(tableName = "person_profile")
data class PersonProfileEntity(
    @PrimaryKey @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "address_form") val addressForm: String,
    @ColumnInfo(name = "birth_year") val birthYear: Int?,
    @ColumnInfo(name = "sex_for_calc") val sexForCalc: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
