package com.healthofatitan.app.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.healthofatitan.app.core.model.mentor.MentorPersona
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** The mentor settings the app keeps on the phone (A38 §10.2). Nothing goes to a server (D-019). */
data class MentorPrefs(
    /** null until the user chose (the onboarding «Пропустить» stores [MentorPersona.DEFAULT]). */
    val persona: MentorPersona? = null,
    /**
     * «Крепкие слова» of Бро (A38 §5.1). Always false in this build: it waits for the founder's
     * decision and the 18+ consent flow (D-030 п. 3), so nothing in the app can turn it on.
     */
    val strongWords: Boolean = false,
) {
    /** The character in effect: the chosen one, or Сенсей (A38 §9). */
    val effective: MentorPersona get() = persona ?: MentorPersona.DEFAULT
}

interface MentorSettings {
    val prefs: Flow<MentorPrefs>

    /** One tap in «Аватар › Наставник» (A38 §9 п. 2); memory, goals and marks stay as they are. */
    suspend fun setPersona(persona: MentorPersona)
}

/** Keys of A38 §10.2; pure so the codec is unit-testable. */
object MentorKeys {
    val PERSONA = stringPreferencesKey("mentor_persona")
    val STRONG_WORDS = booleanPreferencesKey("strong_words_enabled")

    fun decode(p: Preferences): MentorPrefs = MentorPrefs(
        persona = MentorPersona.fromId(p[PERSONA]),
        // Ignored on purpose until D-030 п. 3 is decided (age 18+ and explicit consent).
        strongWords = false,
    )
}

class DataStoreMentorSettings @Inject constructor(
    @MentorStore private val store: DataStore<Preferences>,
) : MentorSettings {

    override val prefs: Flow<MentorPrefs> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map(MentorKeys::decode)

    override suspend fun setPersona(persona: MentorPersona) {
        store.edit { it[MentorKeys.PERSONA] = persona.id }
    }
}

@javax.inject.Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MentorStore

private val Context.mentorDataStore: DataStore<Preferences> by preferencesDataStore(name = "mentor")

@Module
@InstallIn(SingletonComponent::class)
abstract class DataStoreModule {

    @Binds
    abstract fun bindMentorSettings(impl: DataStoreMentorSettings): MentorSettings

    companion object {
        @Provides
        @Singleton
        @MentorStore
        fun provideMentorStore(@ApplicationContext context: Context): DataStore<Preferences> = context.mentorDataStore
    }
}
