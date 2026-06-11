package com.chartmann.knightfall.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.chartmann.knightfall.R

class SoundManager(context: Context) {

    enum class Sound { MOVE, CAPTURE, CHECK, WIN, LOSE, DRAW, NOTIFY }

    @Volatile var enabled: Boolean = true

    private val pool = SoundPool.Builder()
        .setMaxStreams(3)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val ids: Map<Sound, Int> = mapOf(
        Sound.MOVE to pool.load(context, R.raw.snd_move, 1),
        Sound.CAPTURE to pool.load(context, R.raw.snd_capture, 1),
        Sound.CHECK to pool.load(context, R.raw.snd_check, 1),
        Sound.WIN to pool.load(context, R.raw.snd_win, 1),
        Sound.LOSE to pool.load(context, R.raw.snd_lose, 1),
        Sound.DRAW to pool.load(context, R.raw.snd_draw, 1),
        Sound.NOTIFY to pool.load(context, R.raw.snd_notify, 1),
    )

    fun play(sound: Sound) {
        if (!enabled) return
        ids[sound]?.let { pool.play(it, 1f, 1f, 1, 0, 1f) }
    }

    fun release() = pool.release()
}
