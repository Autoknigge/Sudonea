package de.autoknigge.sudonea

import android.content.Context
import java.time.LocalDate

/** Spielstände und Statistik liegen nur lokal auf dem Gerät (SharedPreferences). */
class Store(ctx: Context) {
    private val p = ctx.getSharedPreferences("sudonea", Context.MODE_PRIVATE)

    fun saveGame(key: String, g: GameState?) {
        p.edit().apply { if (g == null || g.won) remove("game_$key") else putString("game_$key", g.encode()) }.apply()
    }

    fun loadGame(key: String): GameState? = p.getString("game_$key", null)?.let { GameState.decode(it) }

    fun played(l: Level) = p.getInt("p_${l.name}", 0)
    fun won(l: Level) = p.getInt("w_${l.name}", 0)
    fun best(l: Level) = p.getInt("b_${l.name}", 0)
    fun streak(): Int {
        val last = p.getString("lastDaily", null) ?: return 0
        val d = runCatching { LocalDate.parse(last) }.getOrNull() ?: return 0
        return if (d >= LocalDate.now().minusDays(1)) p.getInt("streak", 0) else 0
    }
    fun bestStreak() = p.getInt("bestStreak", 0)
    fun dailyDone(date: String) = p.getBoolean("done_$date", false)
    fun dailyCount() = p.getInt("dailyCount", 0)

    fun started(l: Level) = p.edit().putInt("p_${l.name}", played(l) + 1).apply()

    fun finished(g: GameState) {
        val e = p.edit()
        e.putInt("w_${g.level.name}", won(g.level) + 1)
        val b = best(g.level)
        if (g.hints == 0 && (b == 0 || g.seconds < b)) e.putInt("b_${g.level.name}", g.seconds)
        if (g.daily != null && !dailyDone(g.daily)) {
            e.putBoolean("done_${g.daily}", true)
            e.putInt("dailyCount", dailyCount() + 1)
            val last = p.getString("lastDaily", null)
            val yesterday = LocalDate.parse(g.daily).minusDays(1).toString()
            val s = if (last == yesterday) p.getInt("streak", 0) + 1 else 1
            e.putInt("streak", s)
            e.putInt("bestStreak", maxOf(s, bestStreak()))
            e.putString("lastDaily", g.daily)
        }
        e.apply()
    }
}
