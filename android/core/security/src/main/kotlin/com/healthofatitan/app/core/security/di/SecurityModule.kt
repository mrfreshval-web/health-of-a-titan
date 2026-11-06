package com.healthofatitan.app.core.security.di

import android.content.Context
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.healthofatitan.app.core.security.FieldCipher
import com.healthofatitan.app.core.security.TinkFieldCipher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    private const val FIELD_KEYSET_NAME = "hot_field_keyset"
    private const val FIELD_PREFS = "hot_field_keyset_prefs"
    private const val FIELD_MASTER_KEY_URI = "android-keystore://hot_field"

    /** K_field: Tink AES256-GCM keyset, encrypted at rest by an Android Keystore master key. */
    @Provides
    @Singleton
    fun provideFieldCipher(@ApplicationContext context: Context): FieldCipher {
        AeadConfig.register()
        val handle = AndroidKeysetManager.Builder()
            .withSharedPref(context, FIELD_KEYSET_NAME, FIELD_PREFS)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri(FIELD_MASTER_KEY_URI)
            .build()
            .keysetHandle
        return TinkFieldCipher(handle.getPrimitive(RegistryConfiguration.get(), Aead::class.java))
    }
}
