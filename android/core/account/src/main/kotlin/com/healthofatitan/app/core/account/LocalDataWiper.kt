package com.healthofatitan.app.core.account

/**
 * Everything of HoT on this phone goes (A65 §2.3 «Смена аккаунта», §6 «Телефон»): the encrypted
 * database with `event_log`, `safety.crisis` and `ED_CARE` with its `ed_care_keep` mark (D-058
 * п. 5 «удалить всё»), the database key, the device key, the account and WHOOP sessions, the
 * profile, the avatar and every setting. Bound in :app, which knows all the stores.
 *
 * Used by «Выйти», «Выйти везде» (one account per install), «Удалить аккаунт» and when the Worker
 * answers `account_deleted`. The app process ends with the wipe.
 */
fun interface LocalDataWiper {
    suspend fun wipe()
}
