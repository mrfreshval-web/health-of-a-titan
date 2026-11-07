package com.healthofatitan.app.domain.training

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** «Звук тренировки» (A43 §1.2, D-035): one tap before the first workout, changeable any time. */
enum class SoundLevel(val ru: String) {
    SILENT("Тишина"),
    IMPORTANT("Только важное"),
    COACH("Тренер"),
    ;

    companion object {
        val DEFAULT = IMPORTANT

        fun of(name: String?): SoundLevel = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/** Vibration patterns of A43 §1.3 (Pixel primitives). */
enum class Vibe { CLICK, TICK, DOUBLE_CLICK, SPIN, HEAVY }

/** Short tones over the music, 1.5–3 kHz, mixed in without audio focus (A43 §1.1 option 2). */
enum class Tone(val hz: List<Int>, val ms: Int) {
    TICK(listOf(1800), 80),
    READY(listOf(1500, 2250), 120),
}

data class CueOutput(val vibe: Vibe?, val tone: Tone?)

/**
 * A43 §1.2 / §1.3 as a pure function. Principle D-035 «музыка священна»: no event of this table
 * pauses or ducks the music; only «Мне плохо» does, and that is the player's job, not a sound.
 * Voice (the «Тренер» level) comes with the next slice: until then «Тренер» sounds like
 * «Только важное». // TODO(next slice): voice queue with track-gap deadlines (A43 §4.2).
 */
object SoundPolicy {
    fun of(cue: PlayerCue, level: SoundLevel): CueOutput {
        val tones = level != SoundLevel.SILENT
        return when (cue) {
            PlayerCue.SET_START -> CueOutput(Vibe.CLICK, null)
            PlayerCue.COUNTDOWN_TICK, PlayerCue.LAST_SECONDS_TICK -> CueOutput(Vibe.TICK, if (tones) Tone.TICK else null)
            PlayerCue.REST_END -> CueOutput(Vibe.DOUBLE_CLICK, if (tones) Tone.READY else null)
            PlayerCue.SIDE_SWITCH -> CueOutput(Vibe.SPIN, null)
            PlayerCue.FINISHED -> CueOutput(Vibe.DOUBLE_CLICK, if (tones) Tone.READY else null)
            PlayerCue.FEEL_BAD -> CueOutput(Vibe.HEAVY, null)
        }
    }

    /** True for the only event that may pause the music (D-035 p. 1). */
    fun pausesMusic(cue: PlayerCue): Boolean = cue == PlayerCue.FEEL_BAD
}

/**
 * Live heart rate in the player (D-034 p. 2: BLE Heart Rate 0x180D from WHOOP «HR Broadcast» or a
 * chest strap). This slice ships only the contract and [NoHeartRate]: the player works on RPE and
 * timers, as A40 §7.4 allows. // TODO(next slice): BLE implementation in an integration module.
 */
interface HeartRateSource {
    /** Beats per minute, null while nothing is connected. */
    val bpm: StateFlow<Int?>
    val available: Boolean

    fun start()

    fun stop()
}

object NoHeartRate : HeartRateSource {
    private val none = MutableStateFlow<Int?>(null)
    override val bpm: StateFlow<Int?> = none.asStateFlow()
    override val available: Boolean = false

    override fun start() = Unit

    override fun stop() = Unit
}
