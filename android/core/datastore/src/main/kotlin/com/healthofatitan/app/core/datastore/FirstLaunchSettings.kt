package com.healthofatitan.app.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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

/**
 * Where the first launch stands (A66 §0.2, D-065): the step to resume at, whether it is over, and
 * the answers of «Откуда ты идёшь». On the phone only; nothing goes to a server.
 */
data class FirstLaunchPrefs(
    val done: Boolean = false,
    /** The step the person stopped at (a name of the feature's step enum); null = the start. */
    val step: String? = null,
    val tried: Set<String> = emptySet(),
    val worked: Set<String> = emptySet(),
    val different: Set<String> = emptySet(),
)

interface FirstLaunchSettings {
    val prefs: Flow<FirstLaunchPrefs>

    suspend fun setStep(step: String)

    suspend fun setAnswers(tried: Set<String>, worked: Set<String>, different: Set<String>)

    /** Passed, or put off by «Позже»: the home is «Сегодня» from now on. */
    suspend fun finish()
}

object FirstLaunchKeys {
    val DONE = booleanPreferencesKey("first_launch_done")
    val STEP = stringPreferencesKey("first_launch_step")
    val TRIED = stringSetPreferencesKey("first_launch_tried")
    val WORKED = stringSetPreferencesKey("first_launch_worked")
    val DIFFERENT = stringSetPreferencesKey("first_launch_different")

    fun decode(p: Preferences): FirstLaunchPrefs = FirstLaunchPrefs(
        done = p[DONE] == true,
        step = p[STEP],
        tried = p[TRIED].orEmpty(),
        worked = p[WORKED].orEmpty(),
        different = p[DIFFERENT].orEmpty(),
    )
}

class DataStoreFirstLaunchSettings @Inject constructor(
    @FirstLaunchStore private val store: DataStore<Preferences>,
) : FirstLaunchSettings {

    override val prefs: Flow<FirstLaunchPrefs> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map(FirstLaunchKeys::decode)

    override suspend fun setStep(step: String) {
        store.edit { it[FirstLaunchKeys.STEP] = step }
    }

    override suspend fun setAnswers(tried: Set<String>, worked: Set<String>, different: Set<String>) {
        store.edit {
            it[FirstLaunchKeys.TRIED] = tried
            it[FirstLaunchKeys.WORKED] = worked
            it[FirstLaunchKeys.DIFFERENT] = different
        }
    }

    override suspend fun finish() {
        store.edit { it[FirstLaunchKeys.DONE] = true }
    }
}

@javax.inject.Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class FirstLaunchStore

private val Context.firstLaunchDataStore: DataStore<Preferences> by preferencesDataStore(name = "first_launch")

@Module
@InstallIn(SingletonComponent::class)
abstract class FirstLaunchModule {
    @Binds
    abstract fun bindFirstLaunchSettings(impl: DataStoreFirstLaunchSettings): FirstLaunchSettings

    companion object {
        @Provides
        @Singleton
        @FirstLaunchStore
        fun provideFirstLaunchStore(@ApplicationContext context: Context): DataStore<Preferences> = context.firstLaunchDataStore
    }
}
