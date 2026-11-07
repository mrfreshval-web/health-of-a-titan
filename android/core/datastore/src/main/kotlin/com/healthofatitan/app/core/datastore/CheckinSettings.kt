package com.healthofatitan.app.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
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
 * «Что спрашивать» of the state cards (A44 §8, sc7) and the few marks the decks keep. Device only
 * (D-019). Card ids are `cardId@ver` strings; the feature maps them. No answer is ever kept here.
 *
 * @param off cards switched off (or removed after the 14-day offer)
 * @param stack the person's own stack items for «Утро как вчера?» (names as typed, no doses)
 * @param privateAsked the one explicit offer of «Только ты» was answered (D-036 п. 4)
 * @param stormShownAt when the storm net last showed «Как ты на самом деле?» (epoch ms)
 * @param specialistDay the last day the specialist route was offered (`yyyy-MM-dd`)
 */
data class CheckinPrefs(
    val off: Set<String> = emptySet(),
    val quick: Boolean = false,
    val intimacy: Boolean = false,
    val pulls: Boolean = false,
    val links: Boolean = false,
    val ritual: Boolean = true,
    val ritualReminder: Boolean = false,
    val evening: Boolean = true,
    val discoveries: Boolean = true,
    val haptics: Boolean = true,
    val drinks: Boolean = true,
    val stack: List<String> = emptyList(),
    val privateAsked: Boolean = false,
    val stormShownAt: Long? = null,
    val specialistDay: String? = null,
    val removalDismissed: Set<String> = emptySet(),
)

interface CheckinSettings {
    val prefs: Flow<CheckinPrefs>

    suspend fun update(change: (CheckinPrefs) -> CheckinPrefs)
}

object CheckinKeys {
    val OFF = stringSetPreferencesKey("checkin_off")
    val QUICK = booleanPreferencesKey("checkin_quick")
    val INTIMACY = booleanPreferencesKey("checkin_s5_intimacy")
    val PULLS = booleanPreferencesKey("checkin_s5_pulls")
    val LINKS = booleanPreferencesKey("checkin_s5_links")
    val RITUAL = booleanPreferencesKey("checkin_ritual")
    val RITUAL_REMINDER = booleanPreferencesKey("checkin_ritual_reminder")
    val EVENING = booleanPreferencesKey("checkin_evening")
    val DISCOVERIES = booleanPreferencesKey("checkin_discoveries")
    val HAPTICS = booleanPreferencesKey("checkin_haptics")
    val DRINKS = booleanPreferencesKey("checkin_drinks")
    val STACK = stringPreferencesKey("checkin_stack")
    val PRIVATE_ASKED = booleanPreferencesKey("checkin_s5_asked")
    val STORM_AT = longPreferencesKey("checkin_storm_at")
    val SPECIALIST_DAY = stringPreferencesKey("checkin_specialist_day")
    val REMOVAL_DISMISSED = stringSetPreferencesKey("checkin_removal_dismissed")

    private const val SEP = "\u001F"

    fun decode(p: Preferences): CheckinPrefs = CheckinPrefs(
        off = p[OFF].orEmpty(),
        quick = p[QUICK] ?: false,
        intimacy = p[INTIMACY] ?: false,
        pulls = p[PULLS] ?: false,
        links = p[LINKS] ?: false,
        ritual = p[RITUAL] ?: true,
        ritualReminder = p[RITUAL_REMINDER] ?: false,
        evening = p[EVENING] ?: true,
        discoveries = p[DISCOVERIES] ?: true,
        haptics = p[HAPTICS] ?: true,
        drinks = p[DRINKS] ?: true,
        stack = p[STACK]?.split(SEP)?.filter { it.isNotBlank() }.orEmpty(),
        privateAsked = p[PRIVATE_ASKED] ?: false,
        stormShownAt = p[STORM_AT],
        specialistDay = p[SPECIALIST_DAY],
        removalDismissed = p[REMOVAL_DISMISSED].orEmpty(),
    )

    fun encode(c: CheckinPrefs, p: MutablePreferences) {
        p[OFF] = c.off
        p[QUICK] = c.quick
        p[INTIMACY] = c.intimacy
        p[PULLS] = c.pulls
        p[LINKS] = c.links
        p[RITUAL] = c.ritual
        p[RITUAL_REMINDER] = c.ritualReminder
        p[EVENING] = c.evening
        p[DISCOVERIES] = c.discoveries
        p[HAPTICS] = c.haptics
        p[DRINKS] = c.drinks
        p[STACK] = c.stack.joinToString(SEP)
        p[PRIVATE_ASKED] = c.privateAsked
        if (c.stormShownAt != null) p[STORM_AT] = c.stormShownAt else p.remove(STORM_AT)
        if (c.specialistDay != null) p[SPECIALIST_DAY] = c.specialistDay else p.remove(SPECIALIST_DAY)
        p[REMOVAL_DISMISSED] = c.removalDismissed
    }
}

class DataStoreCheckinSettings @Inject constructor(
    @CheckinStore private val store: DataStore<Preferences>,
) : CheckinSettings {

    override val prefs: Flow<CheckinPrefs> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map(CheckinKeys::decode)

    override suspend fun update(change: (CheckinPrefs) -> CheckinPrefs) {
        store.edit { p -> CheckinKeys.encode(change(CheckinKeys.decode(p)), p) }
    }
}

@javax.inject.Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CheckinStore

private val Context.checkinDataStore: DataStore<Preferences> by preferencesDataStore(name = "checkin")

@Module
@InstallIn(SingletonComponent::class)
abstract class CheckinStoreModule {

    @Binds
    abstract fun bindCheckinSettings(impl: DataStoreCheckinSettings): CheckinSettings

    companion object {
        @Provides
        @Singleton
        @CheckinStore
        fun provideCheckinStore(@ApplicationContext context: Context): DataStore<Preferences> = context.checkinDataStore
    }
}
