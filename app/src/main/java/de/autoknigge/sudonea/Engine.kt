package de.autoknigge.sudonea

import java.util.Random

/** Eigene Sudoku-Engine: Löser mit Eindeutigkeitsprüfung, Generator mit Schwierigkeitsgrad und erklärende Tipps. */

enum class Level(val label: String, val clues: IntRange) {
    EASY("Leicht", 36..44),
    MEDIUM("Mittel", 30..35),
    HARD("Schwer", 24..30)
}

enum class Tech(val level: Int, val label: String) {
    NAKED_SINGLE(1, "Einziger Kandidat"),
    HIDDEN_SINGLE(1, "Einzige Möglichkeit in der Einheit"),
    POINTING(2, "Zeiger (Pointing Pair)"),
    CLAIMING(2, "Blockbindung (Box/Line)"),
    NAKED_PAIR(2, "Nacktes Paar"),
    HIDDEN_PAIR(3, "Verstecktes Paar")
}

/** Ein logischer Lösungsschritt. [place] = Zahl eintragen, sonst Kandidaten streichen. */
class Step(
    val tech: Tech,
    val cells: List<Int>,
    val digit: Int,
    val place: Boolean,
    val elim: List<Pair<Int, Int>>,
    val text: String
)

object Sudoku {
    val ROWS = Array(9) { r -> IntArray(9) { c -> r * 9 + c } }
    val COLS = Array(9) { c -> IntArray(9) { r -> r * 9 + c } }
    val BOXES = Array(9) { b -> IntArray(9) { i -> (b / 3 * 3 + i / 3) * 9 + (b % 3 * 3 + i % 3) } }
    val UNITS: List<IntArray> = ROWS.toList() + COLS.toList() + BOXES.toList()
    val PEERS: Array<IntArray> = Array(81) { i ->
        val r = i / 9; val c = i % 9; val b = r / 3 * 3 + c / 3
        (ROWS[r] + COLS[c] + BOXES[b]).filter { it != i }.distinct().toIntArray()
    }

    fun unitName(u: Int) = when {
        u < 9 -> "Zeile ${u + 1}"
        u < 18 -> "Spalte ${u - 8}"
        else -> "Block ${u - 17}"
    }

    fun cellName(i: Int) = "Zeile ${i / 9 + 1}, Spalte ${i % 9 + 1}"

    // ---------- Löser (Rückverfolgung mit Bitmasken) ----------

    /** Zählt Lösungen bis [limit]. */
    fun countSolutions(grid: IntArray, limit: Int = 2): Int {
        val g = grid.copyOf()
        val rows = IntArray(9); val cols = IntArray(9); val boxes = IntArray(9)
        for (i in 0 until 81) if (g[i] != 0) {
            val bit = 1 shl g[i]
            val r = i / 9; val c = i % 9
            if (rows[r] and bit != 0 || cols[c] and bit != 0 || boxes[r / 3 * 3 + c / 3] and bit != 0) return 0
            rows[r] = rows[r] or bit; cols[c] = cols[c] or bit; boxes[r / 3 * 3 + c / 3] = boxes[r / 3 * 3 + c / 3] or bit
        }
        var count = 0
        fun rec(): Boolean {
            var best = -1; var bestMask = 0; var bestN = 10
            for (i in 0 until 81) if (g[i] == 0) {
                val r = i / 9; val c = i % 9
                val m = (rows[r] or cols[c] or boxes[r / 3 * 3 + c / 3]).inv() and 0x3FE
                val n = Integer.bitCount(m)
                if (n < bestN) { bestN = n; best = i; bestMask = m; if (n <= 1) break }
            }
            if (best < 0) { count++; return count >= limit }
            if (bestN == 0) return false
            val r = best / 9; val c = best % 9; val b = r / 3 * 3 + c / 3
            var m = bestMask
            while (m != 0) {
                val bit = m and -m
                m = m xor bit
                g[best] = Integer.numberOfTrailingZeros(bit)
                rows[r] = rows[r] or bit; cols[c] = cols[c] or bit; boxes[b] = boxes[b] or bit
                val stop = rec()
                rows[r] = rows[r] xor bit; cols[c] = cols[c] xor bit; boxes[b] = boxes[b] xor bit
                g[best] = 0
                if (stop) return true
            }
            return false
        }
        rec()
        return count
    }

    /** Eine Lösung oder null. */
    fun solve(grid: IntArray): IntArray? {
        val g = grid.copyOf()
        fun ok(i: Int, d: Int): Boolean = PEERS[i].none { g[it] == d }
        fun rec(): Boolean {
            var best = -1; var bestN = 10
            for (i in 0 until 81) if (g[i] == 0) {
                var n = 0
                for (d in 1..9) if (ok(i, d)) n++
                if (n < bestN) { bestN = n; best = i; if (n <= 1) break }
            }
            if (best < 0) return true
            for (d in 1..9) if (ok(best, d)) {
                g[best] = d
                if (rec()) return true
                g[best] = 0
            }
            return false
        }
        return if (rec()) g else null
    }

    // ---------- Generator ----------

    private fun fullGrid(rnd: Random): IntArray {
        val g = IntArray(81)
        fun ok(i: Int, d: Int) = PEERS[i].none { g[it] == d }
        fun rec(i: Int): Boolean {
            if (i == 81) return true
            val digits = (1..9).toMutableList().also { it.shuffle(rnd) }
            for (d in digits) if (ok(i, d)) {
                g[i] = d
                if (rec(i + 1)) return true
                g[i] = 0
            }
            return false
        }
        rec(0)
        return g
    }

    class Puzzle(val grid: IntArray, val solution: IntArray, val level: Level, val rating: Int)

    /** Entfernt Zahlen punktsymmetrisch, solange die Lösung eindeutig bleibt. */
    private fun carve(full: IntArray, target: Int, rnd: Random): IntArray {
        val g = full.copyOf()
        val order = (0..40).toMutableList().also { it.shuffle(rnd) }
        var clues = 81
        for (i in order) {
            if (clues <= target) break
            val j = 80 - i
            val a = g[i]; val b = g[j]
            g[i] = 0; g[j] = 0
            if (countSolutions(g, 2) == 1) clues -= if (i == j) 1 else 2 else { g[i] = a; g[j] = b }
        }
        return g
    }

    fun rate(grid: IntArray): Int {
        val logic = Logic(grid)
        var max = 1
        while (!logic.solved()) {
            val s = logic.next() ?: return 4
            if (s.tech.level > max) max = s.tech.level
            logic.apply(s)
        }
        return max
    }

    private fun matches(level: Level, rating: Int) = when (level) {
        Level.EASY -> rating == 1
        Level.MEDIUM -> rating == 2
        Level.HARD -> rating >= 3
    }

    fun generate(level: Level, rnd: Random = Random()): Puzzle {
        var fallback: Puzzle? = null
        repeat(80) {
            val full = fullGrid(rnd)
            val target = level.clues.first + rnd.nextInt(level.clues.last - level.clues.first + 1)
            val g = carve(full, target, rnd)
            val rating = rate(g)
            val p = Puzzle(g, full, level, rating)
            if (matches(level, rating)) return p
            if (fallback == null || Math.abs(rating - need(level)) < Math.abs(fallback!!.rating - need(level))) fallback = p
        }
        return fallback!!
    }

    private fun need(level: Level) = when (level) { Level.EASY -> 1; Level.MEDIUM -> 2; Level.HARD -> 3 }

    /** Tagesrätsel: für alle Spieler am selben Datum gleich. */
    fun daily(year: Int, month: Int, day: Int, weekday: Int): Puzzle {
        val level = when (weekday) { 1, 2 -> Level.EASY; 3, 4, 5 -> Level.MEDIUM; else -> Level.HARD }
        return generate(level, Random(year * 10000L + month * 100L + day))
    }
}

/** Logischer Löser mit Kandidatenlisten; liefert Schritt für Schritt erklärte Techniken. */
class Logic(start: IntArray) {
    val g = start.copyOf()
    val cand = IntArray(81)

    init {
        for (i in 0 until 81) cand[i] = if (g[i] != 0) 0 else
            (1..9).fold(0) { m, d -> if (Sudoku.PEERS[i].none { g[it] == d }) m or (1 shl d) else m }
    }

    fun solved() = g.all { it != 0 }

    fun apply(s: Step) {
        if (s.place) {
            val c = s.cells[0]
            g[c] = s.digit
            cand[c] = 0
            Sudoku.PEERS[c].forEach { cand[it] = cand[it] and (1 shl s.digit).inv() }
        } else s.elim.forEach { (c, d) -> cand[c] = cand[c] and (1 shl d).inv() }
    }

    private fun has(c: Int, d: Int) = cand[c] and (1 shl d) != 0
    private fun digits(mask: Int) = (1..9).filter { mask and (1 shl it) != 0 }

    fun next(): Step? =
        nakedSingle() ?: hiddenSingle() ?: pointing() ?: claiming() ?: nakedPair() ?: hiddenPair()

    private fun nakedSingle(): Step? {
        for (i in 0 until 81) if (g[i] == 0 && Integer.bitCount(cand[i]) == 1) {
            val d = Integer.numberOfTrailingZeros(cand[i])
            return Step(Tech.NAKED_SINGLE, listOf(i), d, true, emptyList(),
                "${Sudoku.cellName(i)}: Alle anderen Ziffern sind durch Zeile, Spalte und Block schon ausgeschlossen. Hier muss die $d stehen.")
        }
        return null
    }

    private fun hiddenSingle(): Step? {
        for ((u, unit) in Sudoku.UNITS.withIndex()) for (d in 1..9) {
            val cells = unit.filter { g[it] == 0 && has(it, d) }
            if (cells.size == 1 && unit.none { g[it] == d }) {
                val c = cells[0]
                return Step(Tech.HIDDEN_SINGLE, listOf(c), d, true, emptyList(),
                    "In ${Sudoku.unitName(u)} passt die $d nur noch in ein Feld: ${Sudoku.cellName(c)}.")
            }
        }
        return null
    }

    private fun pointing(): Step? {
        for (b in 0 until 9) for (d in 1..9) {
            val cells = Sudoku.BOXES[b].filter { g[it] == 0 && has(it, d) }
            if (cells.size < 2) continue
            val rowSame = cells.all { it / 9 == cells[0] / 9 }
            val colSame = cells.all { it % 9 == cells[0] % 9 }
            val line = if (rowSame) Sudoku.ROWS[cells[0] / 9] else if (colSame) Sudoku.COLS[cells[0] % 9] else continue
            val elim = line.filter { it !in Sudoku.BOXES[b] && g[it] == 0 && has(it, d) }.map { it to d }
            if (elim.isNotEmpty()) {
                val lu = if (rowSame) "Zeile ${cells[0] / 9 + 1}" else "Spalte ${cells[0] % 9 + 1}"
                return Step(Tech.POINTING, cells, d, false, elim,
                    "In Block ${b + 1} kann die $d nur in $lu stehen. Deshalb darf die $d im Rest von $lu nirgends sonst vorkommen: Streiche sie dort aus den Notizen.")
            }
        }
        return null
    }

    private fun claiming(): Step? {
        for ((u, unit) in Sudoku.UNITS.withIndex()) {
            if (u >= 18) continue
            for (d in 1..9) {
                val cells = unit.filter { g[it] == 0 && has(it, d) }
                if (cells.size < 2) continue
                val box = (cells[0] / 9) / 3 * 3 + (cells[0] % 9) / 3
                if (!cells.all { (it / 9) / 3 * 3 + (it % 9) / 3 == box }) continue
                val elim = Sudoku.BOXES[box].filter { it !in unit && g[it] == 0 && has(it, d) }.map { it to d }
                if (elim.isNotEmpty()) return Step(Tech.CLAIMING, cells, d, false, elim,
                    "In ${Sudoku.unitName(u)} liegt die $d nur in Block ${box + 1}. Damit ist sie dort belegt: Streiche die $d aus den übrigen Feldern von Block ${box + 1}.")
            }
        }
        return null
    }

    private fun nakedPair(): Step? {
        for ((u, unit) in Sudoku.UNITS.withIndex()) {
            val two = unit.filter { g[it] == 0 && Integer.bitCount(cand[it]) == 2 }
            for (a in two.indices) for (b in a + 1 until two.size) {
                if (cand[two[a]] != cand[two[b]]) continue
                val m = cand[two[a]]
                val elim = unit.filter { it != two[a] && it != two[b] && g[it] == 0 && cand[it] and m != 0 }
                    .flatMap { c -> digits(cand[c] and m).map { c to it } }
                if (elim.isNotEmpty()) {
                    val ds = digits(m)
                    return Step(Tech.NAKED_PAIR, listOf(two[a], two[b]), ds[0], false, elim,
                        "In ${Sudoku.unitName(u)} haben zwei Felder nur noch die Kandidaten ${ds[0]} und ${ds[1]}. Diese beiden Zahlen gehören diesen zwei Feldern: Streiche sie aus den übrigen Feldern.")
                }
            }
        }
        return null
    }

    private fun hiddenPair(): Step? {
        for ((u, unit) in Sudoku.UNITS.withIndex()) {
            for (d1 in 1..8) for (d2 in d1 + 1..9) {
                val c1 = unit.filter { g[it] == 0 && has(it, d1) }
                val c2 = unit.filter { g[it] == 0 && has(it, d2) }
                if (c1.size != 2 || c1 != c2) continue
                val keep = (1 shl d1) or (1 shl d2)
                val elim = c1.flatMap { c -> digits(cand[c] and keep.inv()).map { c to it } }
                if (elim.isNotEmpty()) return Step(Tech.HIDDEN_PAIR, c1, d1, false, elim,
                    "In ${Sudoku.unitName(u)} kommen $d1 und $d2 nur in denselben zwei Feldern vor. Dort können keine anderen Zahlen stehen: Streiche die übrigen Kandidaten dieser zwei Felder.")
            }
        }
        return null
    }
}
