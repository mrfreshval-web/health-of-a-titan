package com.healthofatitan.app.feature.training

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.healthofatitan.app.domain.training.SoundLevel
import com.healthofatitan.app.domain.training.TrainingLibrary
import com.healthofatitan.app.domain.training.TrainingPlace
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The training library from the APK assets (`contentCompile`: content/training/x.yaml →
 * assets/content/training/x.json). Read once, off the main thread, on first use.
 */
@Singleton
class TrainingContent @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val mutex = Mutex()

    @Volatile
    private var cached: TrainingLibrary? = null

    suspend fun library(): TrainingLibrary = cached ?: mutex.withLock {
        cached ?: withContext(Dispatchers.IO) { load() }.also { cached = it }
    }

    private fun read(path: String): String? = runCatching {
        context.assets.open(path).bufferedReader().use { it.readText() }
    }.getOrNull()

    private fun load(): TrainingLibrary = TrainingLibrary.fromJson(
        exercises = read(TrainingLibrary.EXERCISES_ASSET) ?: error("missing ${TrainingLibrary.EXERCISES_ASSET}"),
        templates = read(TrainingLibrary.TEMPLATES_ASSET) ?: error("missing ${TrainingLibrary.TEMPLATES_ASSET}"),
        rules = read(TrainingLibrary.RULES_ASSET) ?: error("missing ${TrainingLibrary.RULES_ASSET}"),
        poses = read(TrainingLibrary.POSES_ASSET),
    )
}

/**
 * Two per-device choices of the training screen: where the workout happens and «Звук
 * тренировки» (A43 §1.2, default «Только важное»). Not health data, so plain preferences.
 */
@Singleton
class TrainingPrefs @Inject constructor(@param:ApplicationContext context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val _place = MutableStateFlow(TrainingPlace.entries.firstOrNull { it.name == prefs.getString(KEY_PLACE, null) } ?: TrainingPlace.HOME)
    val place: StateFlow<TrainingPlace> = _place.asStateFlow()

    private val _sound = MutableStateFlow(SoundLevel.of(prefs.getString(KEY_SOUND, null)))
    val sound: StateFlow<SoundLevel> = _sound.asStateFlow()

    fun setPlace(place: TrainingPlace) {
        prefs.edit { putString(KEY_PLACE, place.name) }
        _place.value = place
    }

    fun setSound(level: SoundLevel) {
        prefs.edit { putString(KEY_SOUND, level.name) }
        _sound.value = level
    }

    private companion object {
        const val FILE = "training"
        const val KEY_PLACE = "place"
        const val KEY_SOUND = "sound"
    }
}
