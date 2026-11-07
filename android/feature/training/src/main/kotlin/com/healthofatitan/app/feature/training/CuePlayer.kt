package com.healthofatitan.app.feature.training

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.healthofatitan.app.domain.training.PlayerCue
import com.healthofatitan.app.domain.training.SoundLevel
import com.healthofatitan.app.domain.training.SoundPolicy
import com.healthofatitan.app.domain.training.Tone
import com.healthofatitan.app.domain.training.Vibe
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * Plays the player's events as A43 §1.3 says ([SoundPolicy]): Pixel vibration primitives and
 * short sine tones mixed over the music without audio focus (USAGE_ASSISTANCE_SONIFICATION),
 * so the music never pauses or ducks (D-035). Only «Мне плохо» takes transient focus, which
 * pauses the music, and gives it back when the workout resumes or closes.
 */
@Singleton
class CuePlayer @Inject constructor(@param:ApplicationContext private val context: Context) {

    private val vibrator: Vibrator? by lazy {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    }
    private val audio: AudioManager? by lazy { context.getSystemService(AudioManager::class.java) }
    private var focus: AudioFocusRequest? = null

    fun play(cue: PlayerCue, level: SoundLevel) {
        val out = SoundPolicy.of(cue, level)
        out.vibe?.let(::vibrate)
        out.tone?.let(::tone)
        if (SoundPolicy.pausesMusic(cue)) pauseMusic()
    }

    /** «Мне плохо»: everything stops, the music too (D-035 p. 1). */
    private fun pauseMusic() {
        val am = audio ?: return
        if (focus != null) return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setOnAudioFocusChangeListener { }
            .build()
        runCatching { am.requestAudioFocus(request) }
        focus = request
    }

    /** Gives the music back (resume after a pause, or the workout closed). */
    fun releaseMusic() {
        val request = focus ?: return
        runCatching { audio?.abandonAudioFocusRequest(request) }
        focus = null
    }

    private fun vibrate(vibe: Vibe) {
        val v = vibrator?.takeIf { it.hasVibrator() } ?: return
        val effect = when (vibe) {
            Vibe.CLICK -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
            Vibe.TICK -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
            Vibe.DOUBLE_CLICK -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
            Vibe.HEAVY -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
            Vibe.SPIN ->
                if (v.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_SPIN)) {
                    VibrationEffect.startComposition().addPrimitive(VibrationEffect.Composition.PRIMITIVE_SPIN).compose()
                } else {
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
                }
        }
        // Alarm usage: a timer the user asked for, felt even with touch feedback turned off.
        runCatching { v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM)) }
    }

    private fun tone(tone: Tone) {
        val pcm = samples(tone)
        runCatching {
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.size * 2)
                .build()
            track.write(pcm, 0, pcm.size)
            track.setNotificationMarkerPosition(pcm.size)
            track.setPlaybackPositionUpdateListener(
                object : AudioTrack.OnPlaybackPositionUpdateListener {
                    override fun onMarkerReached(t: AudioTrack) {
                        t.release()
                    }

                    override fun onPeriodicNotification(t: AudioTrack) = Unit
                },
            )
            track.play()
        }
    }

    /** One or two short sines with a soft edge (no click), quiet: a tick, not an alarm. */
    private fun samples(tone: Tone): ShortArray {
        val per = RATE * tone.ms / 1000
        val gap = RATE * GAP_MS / 1000
        val out = ShortArray(per * tone.hz.size + gap * (tone.hz.size - 1))
        var at = 0
        tone.hz.forEachIndexed { k, hz ->
            val fade = min(per / 4, RATE * FADE_MS / 1000)
            for (i in 0 until per) {
                val edge = min(1.0, min(i, per - 1 - i).toDouble() / fade)
                out[at + i] = (sin(2 * PI * hz * i / RATE) * edge * VOLUME * Short.MAX_VALUE).toInt().toShort()
            }
            at += per + if (k < tone.hz.size - 1) gap else 0
        }
        return out
    }

    private companion object {
        const val RATE = 44_100
        const val GAP_MS = 40
        const val FADE_MS = 8
        const val VOLUME = 0.35
    }
}
