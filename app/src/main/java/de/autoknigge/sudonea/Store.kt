package de.autoknigge.sudonea

import android.content.Context
import java.time.LocalDate

/** Spielstände und Statistik liegen nur lokal auf dem Gerät (SharedPreferences). */
class Store(ctx: Context) {
    private val p = ctx.getSharedPreferences("sudonea", Context.MODE_PRIVATE)

    /** Alle angefangenen Rätsel (neueste zuerst). */
    fun gameIds(): List<String> = (p.getString("games", "") ?: "").split(";").filter { it.isNotBlank() }

    fun saveGame(key: String, g: GameState?) {
        val ids = gameIds().toMutableList()
        val e = p.edit()
        if (g == null || g.won) { e.remove("game_$key"); ids.remove(key) }
        else { e.putString("game_$key", g.encode()); if (key !in ids) ids.add(0, key) }
        e.putString("games", ids.joinToString(";")).apply()
    }

    fun loadGame(key: String): GameState? = p.getString("game_$key", null)?.let { GameState.decode(it) }

    fun deleteGame(key: String) = saveGame(key, null)

    /** Bearbeitete (gelöste) Rätsel: "Stufe|Nummer|Tag|Sekunden|Fehler|Tipps|Datum". */
    class Done(val raw: String) {
        private val a = raw.split("|")
        val level: Level? get() = runCatching { Level.valueOf(a[0]) }.getOrNull()
        val number get() = a.getOrNull(1)?.toIntOrNull() ?: 0
        val daily get() = a.getOrNull(2)?.takeIf { it != "-" }
        val seconds get() = a.getOrNull(3)?.toIntOrNull() ?: 0
        val mistakes get() = a.getOrNull(4)?.toIntOrNull() ?: 0
        val hints get() = a.getOrNull(5)?.toIntOrNull() ?: 0
        val date get() = a.getOrNull(6) ?: ""
    }

    fun history(): List<Done> = (p.getString("history", "") ?: "").split("\n").filter { it.isNotBlank() }.map { Done(it) }

    fun deleteHistory(index: Int) {
        val l = (p.getString("history", "") ?: "").split("\n").filter { it.isNotBlank() }.toMutableList()
        if (index in l.indices) l.removeAt(index)
        p.edit().putString("history", l.joinToString("\n")).apply()
    }

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
        val line = listOf(g.level.name, g.number, g.daily ?: "-", g.seconds, g.mistakes, g.hints, LocalDate.now()).joinToString("|")
        val l = listOf(line) + (p.getString("history", "") ?: "").split("\n").filter { it.isNotBlank() }
        e.putString("history", l.take(300).joinToString("\n"))
        e.apply()
    }
}
