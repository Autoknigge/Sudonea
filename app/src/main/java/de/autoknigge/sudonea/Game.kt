package de.autoknigge.sudonea

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class HintInfo(val text: String, val cells: List<Int>, val cell: Int?, val digit: Int, val elim: List<Pair<Int, Int>>)

/** Ein laufendes Spiel: Zahlen, Notizen, Rückgängig, Tipps. */
class GameState(val grid: IntArray, val solution: IntArray, val level: Level, val daily: String?) {
    val vals = mutableStateListOf<Int>().apply { addAll(grid.toList()) }
    val notes = mutableStateListOf<Int>().apply { repeat(81) { add(0) } }
    var sel by mutableIntStateOf(-1)
    var noteMode by mutableStateOf(false)
    var seconds by mutableIntStateOf(0)
    var mistakes by mutableIntStateOf(0)
    var hints by mutableIntStateOf(0)
    var won by mutableStateOf(false)
    var hint by mutableStateOf<HintInfo?>(null)
    private val undo = ArrayList<Pair<IntArray, IntArray>>()

    fun isGiven(i: Int) = grid[i] != 0
    fun isWrong(i: Int) = vals[i] != 0 && vals[i] != solution[i]
    fun count(d: Int) = vals.count { it == d }

    private fun push() {
        undo.add(vals.toIntArray() to notes.toIntArray())
        if (undo.size > 200) undo.removeAt(0)
    }

    fun undo() {
        if (won || undo.isEmpty()) return
        val (v, n) = undo.removeAt(undo.size - 1)
        for (i in 0 until 81) { vals[i] = v[i]; notes[i] = n[i] }
        hint = null
    }

    fun enter(d: Int) {
        val s = sel
        if (s < 0 || isGiven(s) || won) return
        hint = null
        push()
        if (noteMode) {
            if (vals[s] == 0) notes[s] = notes[s] xor (1 shl d)
            return
        }
        if (vals[s] == d) { vals[s] = 0; return }
        vals[s] = d
        notes[s] = 0
        if (d != solution[s]) mistakes++
        else Sudoku.PEERS[s].forEach { notes[it] = notes[it] and (1 shl d).inv() }
        if ((0 until 81).all { vals[it] == solution[it] }) won = true
    }

    fun erase() {
        val s = sel
        if (s < 0 || isGiven(s) || won) return
        push(); hint = null
        vals[s] = 0; notes[s] = 0
    }

    /** Trägt alle noch möglichen Kandidaten als Notizen ein. */
    fun autoNotes() {
        if (won) return
        push(); hint = null
        for (i in 0 until 81) {
            if (vals[i] != 0) { notes[i] = 0; continue }
            var m = 0
            for (d in 1..9) if (Sudoku.PEERS[i].none { vals[it] == d }) m = m or (1 shl d)
            notes[i] = m
        }
    }

    fun giveHint() {
        if (won) return
        val bad = (0 until 81).firstOrNull { isWrong(it) }
        if (bad != null) {
            sel = bad
            hint = HintInfo("In ${Sudoku.cellName(bad)} steht eine falsche Zahl (rot markiert). Lösche sie oder trage die richtige ein.", listOf(bad), null, 0, emptyList())
            return
        }
        val step = Logic(vals.toIntArray()).next()
        hints++
        hint = if (step != null) {
            if (step.place) {
                sel = step.cells[0]
                HintInfo("${step.tech.label}: ${step.text}", step.cells, step.cells[0], step.digit, emptyList())
            } else HintInfo("${step.tech.label}: ${step.text}", step.cells, null, step.digit, step.elim)
        } else {
            val c = (0 until 81).firstOrNull { vals[it] == 0 }
            if (c == null) null else {
                sel = c
                HintInfo("Für dieses Rätsel braucht es eine schwierigere Technik. Hilfe: In ${Sudoku.cellName(c)} steht die ${solution[c]}.", listOf(c), c, solution[c], emptyList())
            }
        }
    }

    fun applyHint() {
        val h = hint ?: return
        if (h.cell != null) {
            sel = h.cell
            val wasNote = noteMode
            noteMode = false
            if (vals[h.cell] != h.digit) enter(h.digit)
            noteMode = wasNote
        } else if (h.elim.isNotEmpty()) {
            push()
            h.elim.forEach { (c, d) -> notes[c] = notes[c] and (1 shl d).inv() }
        }
        hint = null
    }

    fun encode(): String = listOf(
        "v1", level.name, daily ?: "-", seconds, mistakes, hints,
        grid.joinToString(""), solution.joinToString(""), vals.joinToString(""), notes.joinToString(",")
    ).joinToString("|")

    companion object {
        fun decode(s: String): GameState? = try {
            val a = s.split("|")
            val g = GameState(
                a[6].map { it - '0' }.toIntArray(), a[7].map { it - '0' }.toIntArray(),
                Level.valueOf(a[1]), a[2].takeIf { it != "-" }
            )
            g.seconds = a[3].toInt(); g.mistakes = a[4].toInt(); g.hints = a[5].toInt()
            a[8].forEachIndexed { i, ch -> g.vals[i] = ch - '0' }
            a[9].split(",").forEachIndexed { i, n -> g.notes[i] = n.toInt() }
            g
        } catch (e: Exception) { null }

        fun of(p: Sudoku.Puzzle, daily: String?) = GameState(p.grid, p.solution, p.level, daily)
    }
}
