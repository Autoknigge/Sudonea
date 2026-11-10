package de.autoknigge.sudonea

import android.content.Context
import android.media.SoundPool
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Tastentöne, an und aus im Menü (gemerkt). */
object Sfx {
    var on by mutableStateOf(true)
    private var pool: SoundPool? = null
    private var id = 0
    private var prefs: android.content.SharedPreferences? = null

    fun init(ctx: Context) {
        if (pool != null) return
        prefs = ctx.applicationContext.getSharedPreferences("sudonea", Context.MODE_PRIVATE)
        on = prefs?.getBoolean("sfx", true) ?: true
        pool = SoundPool.Builder().setMaxStreams(3).build().also { id = it.load(ctx.applicationContext, R.raw.click, 1) }
    }

    fun set(v: Boolean) {
        on = v
        prefs?.edit()?.putBoolean("sfx", v)?.apply()
    }

    fun click() {
        if (on) pool?.play(id, 0.7f, 0.7f, 1, 0, 1f)
    }
}
