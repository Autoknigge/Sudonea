package de.autoknigge.sudonea

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class HintInfo(val text: String, val cells: List<Int>, val cell: Int?, val digit: Int, val elim: List<Pair<Int, Int>>)

/** Ein laufendes Spiel: Zahlen, Notizen, Rückgängig, Tipps. */
class GameState(val grid: IntArray, val solution: IntArray, val level: Level, val daily: String?, val number: Int = 0) {
    val vals = mutableStateListOf<Int>().apply { addAll(grid.toList()) }
    val notes = mutableStateListOf<Int>().apply { repeat(81) { add(0) } }
    var sel by mutableIntStateOf(-1)
    var noteMode by mutableStateOf(false)
    var seconds by mutableIntStateOf(0)
    var mistakes by mutableIntStateOf(0)
    var hints by mutableIntStateOf(0)
    var won by mutableStateOf(false)
    var hint by mutableStateOf<HintInfo?>(null)
    var paused by mutableStateOf(false)
    /** Feld, in dem die gewünschte Notiz zum Eintrag gewählt wird (mehrere Notizen). */
    var chooseCell by mutableIntStateOf(-1)
    /** Hervorgehobene Ziffer (alle gleichen Zahlen und Notizen leuchten) und Ziffer-zuerst-Modus. */
    var hd by mutableIntStateOf(0)
    var digitFirst by mutableStateOf(false)
    private val undo = ArrayList<Pair<IntArray, IntArray>>()
    private val redo = ArrayList<Pair<IntArray, IntArray>>()
    val canUndo get() = undo.isNotEmpty()
    val canRedo get() = redo.isNotEmpty()

    val id: String get() = daily?.let { "daily-$it" } ?: "${level.name}-$number"

    fun isGiven(i: Int) = grid[i] != 0
    fun isWrong(i: Int) = vals[i] != 0 && vals[i] != solution[i]
    fun count(d: Int) = vals.count { it == d }

    private fun push() {
        redo.clear()
        undo.add(vals.toIntArray() to notes.toIntArray())
        if (undo.size > 200) undo.removeAt(0)
    }

    fun undo() {
        if (won || undo.isEmpty()) return
        redo.add(vals.toIntArray() to notes.toIntArray())
        val (v, n) = undo.removeAt(undo.size - 1)
        for (i in 0 until 81) { vals[i] = v[i]; notes[i] = n[i] }
        hint = null
    }

    fun redo() {
        if (won || redo.isEmpty()) return
        undo.add(vals.toIntArray() to notes.toIntArray())
        val (v, n) = redo.removeAt(redo.size - 1)
        for (i in 0 until 81) { vals[i] = v[i]; notes[i] = n[i] }
        hint = null
    }

    /** Tippen auf ein Feld: im Ziffer-zuerst-Modus wird die gewählte Zahl dort eingetragen. */
    fun tapCell(i: Int) {
        if (paused || won) return
        if (digitFirst && hd != 0 && !isGiven(i)) { sel = i; enter(hd); return }
        sel = i
        digitFirst = false
        hd = vals[i]
    }

    /** Langes Drücken auf ein Feld mit Notizen: aus der Notiz wird ein Eintrag. */
    fun longPress(i: Int) {
        if (paused || won || isGiven(i)) return
        sel = i
        // Eigene Zahl lange gedrückt: wieder löschen (Notizmodus bleibt unverändert)
        if (vals[i] != 0) { erase(); return }
        // Zahl ist vorgewählt: langes Drücken setzt sie direkt als Eintrag, auch ohne vorherige Notiz
        if (digitFirst && hd != 0) { placeNote(i, hd); return }
        val n = notes[i]
        val ds = (1..9).filter { n and (1 shl it) != 0 }
        when {
            ds.isEmpty() -> return
            hd != 0 && hd in ds -> placeNote(i, hd)
            ds.size == 1 -> placeNote(i, ds[0])
            else -> chooseCell = i
        }
    }

    fun placeNote(i: Int, d: Int) {
        sel = i
        chooseCell = -1
        val nm = noteMode
        noteMode = false
        enter(d)
        noteMode = nm
        hd = d
    }

    /** Zifferntaste: trägt ein, wenn ein Feld gewählt ist, sonst Ziffer-zuerst-Modus. */
    fun tapDigit(d: Int) {
        if (paused || won) return
        if (digitFirst && hd == d) { digitFirst = false; hd = 0; return }
        if (sel >= 0 && !isGiven(sel) && !digitFirst) { enter(d); hd = d; return }
        hd = d; digitFirst = true; sel = -1
    }

    /** Prüft die Zahlen: Anzahl falscher Felder. */
    fun wrongCount() = (0 until 81).count { isWrong(it) }

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
        "v2", level.name, daily ?: "-", seconds, mistakes, hints,
        grid.joinToString(""), solution.joinToString(""), vals.joinToString(""), notes.joinToString(","), number
    ).joinToString("|")

    companion object {
        fun decode(s: String): GameState? = try {
            val a = s.split("|")
            val g = GameState(
                a[6].map { it - '0' }.toIntArray(), a[7].map { it - '0' }.toIntArray(),
                Level.valueOf(a[1]), a[2].takeIf { it != "-" }, a.getOrNull(10)?.toIntOrNull() ?: 0
            )
            g.seconds = a[3].toInt(); g.mistakes = a[4].toInt(); g.hints = a[5].toInt()
            a[8].forEachIndexed { i, ch -> g.vals[i] = ch - '0' }
            a[9].split(",").forEachIndexed { i, n -> g.notes[i] = n.toInt() }
            g
        } catch (e: Exception) { null }

        fun of(p: Sudoku.Puzzle, daily: String?, number: Int = 0) = GameState(p.grid, p.solution, p.level, daily, number)
    }
}
