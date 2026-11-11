package de.autoknigge.sudonea

import android.content.Context
import android.media.SoundPool
import androidx.compose.runtime.mutableStateOf

/** Tastentöne, an und aus im Menü (gemerkt). */
object Sfx {
    private val onState = mutableStateOf(true)
    val on: Boolean get() = onState.value
    private val errState = mutableStateOf(false)
    val showErr: Boolean get() = errState.value
    fun setShowErr(v: Boolean) { errState.value = v; prefs?.edit()?.putBoolean("showerr", v)?.apply() }
    private var pool: SoundPool? = null
    private var id = 0
    private var prefs: android.content.SharedPreferences? = null

    fun init(ctx: Context) {
        if (pool != null) return
        prefs = ctx.applicationContext.getSharedPreferences("sudonea", Context.MODE_PRIVATE)
        onState.value = prefs?.getBoolean("sfx", true) ?: true
        errState.value = prefs?.getBoolean("showerr", false) ?: false
        pool = SoundPool.Builder().setMaxStreams(3).build().also { id = it.load(ctx.applicationContext, R.raw.click, 1) }
    }

    fun set(v: Boolean) {
        onState.value = v
        prefs?.edit()?.putBoolean("sfx", v)?.apply()
    }

    fun click() {
        if (on) pool?.play(id, 0.7f, 0.7f, 1, 0, 1f)
    }
}
