package com.healthofatitan.app.core.account.di

import android.content.Context
import android.os.Build
import com.healthofatitan.app.core.account.AccountConfig
import com.healthofatitan.app.core.account.AccountProfileStore
import com.healthofatitan.app.core.account.FileProfileBlobs
import com.healthofatitan.app.core.account.GoogleProfileImport
import com.healthofatitan.app.core.account.AccountCredentialStore
import com.healthofatitan.app.core.account.AccountSession
import com.healthofatitan.app.core.account.AuthApi
import com.healthofatitan.app.core.account.CredentialManagerGoogleIdSource
import com.healthofatitan.app.core.account.DeviceKey
import com.healthofatitan.app.core.account.EncryptedAccountCredentialStore
import com.healthofatitan.app.core.account.GoogleIdSource
import com.healthofatitan.app.core.account.KeystoreDeviceKey
import com.healthofatitan.app.core.network.EdgeConfig
import com.healthofatitan.app.core.security.FieldCipher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.io.File
import java.time.Clock
import javax.inject.Singleton

/** The account (A65, D-062). [AccountConfig] is bound in :app from BuildConfig. */
@Module
@InstallIn(SingletonComponent::class)
object AccountModule {

    @Provides
    @Singleton
    fun provideDeviceKey(): DeviceKey = KeystoreDeviceKey()

    @Provides
    @Singleton
    fun provideAccountStore(@ApplicationContext context: Context, cipher: FieldCipher, json: Json): AccountCredentialStore =
        EncryptedAccountCredentialStore(context, cipher, json)

    @Provides
    @Singleton
    fun provideAuthApi(config: EdgeConfig, http: OkHttpClient, json: Json): AuthApi = AuthApi(config, http, json)

    @Provides
    @Singleton
    fun provideAccountSession(api: AuthApi, store: AccountCredentialStore, key: DeviceKey): AccountSession =
        AccountSession(api, store, key, Clock.systemUTC(), deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim())

    @Provides
    @Singleton
    fun provideGoogleIdSource(config: AccountConfig): GoogleIdSource = CredentialManagerGoogleIdSource(config)

    /** The profile of the account on the phone (A65 §2.4, §5): name, body data, avatar, «О себе». */
    @Provides
    @Singleton
    fun provideAccountProfile(@ApplicationContext context: Context, cipher: FieldCipher, json: Json): AccountProfileStore =
        AccountProfileStore(FileProfileBlobs(File(context.filesDir, AccountProfileStore.DIR)), cipher, json)

    @Provides
    @Singleton
    fun provideGoogleProfileImport(profile: AccountProfileStore, http: OkHttpClient): GoogleProfileImport = GoogleProfileImport(profile, http)
}
