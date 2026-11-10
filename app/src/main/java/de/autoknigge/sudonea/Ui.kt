package de.autoknigge.sudonea

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

val Forest = Color(0xFF0B2A16)
val Panel = Color(0xFF12381F)
val Peer = Color(0xFF1B4A2A)
val SameDigit = Color(0xFF2F6B3E)
val Gold = Color(0xFFE0B23E)
val Ink = Color(0xFFF2F0E0)
val Muted = Color(0xFFB8C7B5)
val Bad = Color(0xFFFF6B5E)

@Composable
fun SudoneaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Gold, onPrimary = Color(0xFF1A1400), background = Forest, surface = Panel,
            onSurface = Ink, onBackground = Ink, secondary = Gold
        ), content = content
    )
}

private sealed interface Screen {
    data object Home : Screen
    data object Stats : Screen
    data object Info : Screen
    data class Play(val game: GameState, val key: String) : Screen
}

fun fmtTime(s: Int) = "%d:%02d".format(s / 60, s % 60)

@Composable
fun Root(store: Store, ads: AdsController) {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun start(level: Level, daily: String?) {
        val key = daily?.let { "daily" } ?: level.name
        val saved = store.loadGame(key)
        if (saved != null && saved.daily == daily) { screen = Screen.Play(saved, key); return }
        busy = true
        scope.launch {
            val number = if (daily == null) (1..99999).random() else 0
            val p = withContext(Dispatchers.Default) {
                if (daily == null) Sudoku.generate(level, java.util.Random(number.toLong() * 31 + level.ordinal))
                else { val d = LocalDate.parse(daily); Sudoku.daily(d.year, d.monthValue, d.dayOfMonth, d.dayOfWeek.value) }
            }
            val g = GameState.of(p, daily, number)
            store.started(g.level)
            store.saveGame(key, g)
            busy = false
            screen = Screen.Play(g, key)
        }
    }

    Column(Modifier.fillMaxSize().background(Forest).statusBarsPadding()) {
        Box(Modifier.weight(1f)) {
            when (val s = screen) {
                Screen.Home -> HomeScreen(store, ads, busy, ::start, { screen = Screen.Stats }, { screen = Screen.Info })
                Screen.Stats -> StatsScreen(store) { screen = Screen.Home }
                Screen.Info -> InfoScreen(ads) { screen = Screen.Home }
                is Screen.Play -> GameScreen(s.game, store, s.key, { screen = Screen.Home }, { lv -> screen = Screen.Home; start(lv, null) })
            }
        }
        BannerBar(ads)
        Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun GoldButton(text: String, sub: String? = null, onClick: () -> Unit) {
    Button(
        onClick = onClick, modifier = Modifier.fillMaxWidth().height(60.dp), shape = RoundedCornerShape(30.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color(0xFF1A1400))
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (sub != null) Text(sub, fontSize = 12.sp)
        }
    }
}

@Composable
fun HomeScreen(store: Store, ads: AdsController, busy: Boolean, start: (Level, String?) -> Unit, stats: () -> Unit, info: () -> Unit) {
    val today = LocalDate.now().toString()
    val done = store.dailyDone(today)
    val streak = store.streak()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Image(painterResource(R.drawable.logo_header), null, Modifier.fillMaxWidth().height(150.dp), contentScale = ContentScale.Fit)
        if (busy) Text("Rätsel wird erstellt …", color = Gold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        GoldButton(
            "★ Tagesrätsel", if (done) "heute gelöst ✓" + (if (streak > 0) " · Serie $streak" else "")
            else "für alle gleich" + (if (streak > 0) " · Serie $streak" else "")
        ) { start(Level.EASY, today) }
        Level.values().forEach { l ->
            val saved = store.loadGame(l.name)
            OutlinedButton(
                onClick = { start(l, null) }, modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp), border = androidx.compose.foundation.BorderStroke(1.5.dp, Gold)
            ) {
                Text(l.label + (if (saved != null) "  ·  Fortsetzen (${fmtTime(saved.seconds)})" else "  ·  Neues Rätsel"), color = Ink, fontSize = 16.sp)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            TextButton(onClick = stats) { Text("Statistik", color = Gold) }
            TextButton(onClick = info) { Text("Info & Datenschutz", color = Gold) }
        }
    }
}

@Composable
fun StatsScreen(store: Store, back: () -> Unit) {
    BackHandler(onBack = back)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Statistik", fontSize = 26.sp, color = Gold, fontWeight = FontWeight.Bold)
        Level.values().forEach { l ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel).padding(16.dp)) {
                Text(l.label, color = Gold, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Gelöst: ${store.won(l)} von ${store.played(l)} begonnen", color = Ink)
                Text("Bestzeit (ohne Tipp): " + (store.best(l).takeIf { it > 0 }?.let { fmtTime(it) } ?: "–"), color = Ink)
            }
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel).padding(16.dp)) {
            Text("Tagesrätsel", color = Gold, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("Gelöst: ${store.dailyCount()}", color = Ink)
            Text("Aktuelle Serie: ${store.streak()} Tage · Beste Serie: ${store.bestStreak()}", color = Ink)
        }
        OutlinedButton(onClick = back, modifier = Modifier.fillMaxWidth()) { Text("Zurück", color = Ink) }
    }
}

@Composable
fun InfoScreen(ads: AdsController, back: () -> Unit) {
    val ctx = LocalContext.current
    BackHandler(onBack = back)
    fun open(url: String) = ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Info", fontSize = 26.sp, color = Gold, fontWeight = FontWeight.Bold)
        Text("Sudonea erstellt jedes Rätsel auf deinem Gerät und prüft, dass es genau eine Lösung hat. Der Tipp erklärt dir die nächste Technik, statt nur die Zahl zu verraten. Spielstände und Statistik bleiben auf deinem Gerät.", color = Ink)
        Text("Die Werbung am unteren Rand finanziert die App.", color = Muted)
        OutlinedButton(onClick = { open("https://autoknigge.de/sudonea/datenschutz.html") }, modifier = Modifier.fillMaxWidth()) { Text("Datenschutzerklärung", color = Ink) }
        if (ads.privacyOptionsRequired) OutlinedButton(onClick = { ads.showPrivacyOptions() }, modifier = Modifier.fillMaxWidth()) { Text("Werbe-Einwilligung ändern", color = Ink) }
        Text("Eine Idee von autoknigge.de", color = Gold, modifier = Modifier.clickable { open("https://autoknigge.de") })
        OutlinedButton(onClick = back, modifier = Modifier.fillMaxWidth()) { Text("Zurück", color = Ink) }
    }
}

@Composable
fun GameScreen(g: GameState, store: Store, key: String, home: () -> Unit, next: (Level) -> Unit) {
    BackHandler { store.saveGame(key, g); home() }
    var menu by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(g, g.paused) {
        var n = 0
        while (!g.won && !g.paused) {
            delay(1000); g.seconds++
            if (++n % 10 == 0) store.saveGame(key, g)
        }
    }
    LaunchedEffect(g.won) {
        if (g.won) { store.finished(g); store.saveGame(key, null) }
    }
    LaunchedEffect(g.vals.toList(), g.notes.toList()) { store.saveGame(key, g) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { store.saveGame(key, g); home() }) { Text("←", color = Gold, fontSize = 22.sp) }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { menu = true }) { Text("⋮", color = Gold, fontSize = 22.sp) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                if (g.daily != null) "★ Tagesrätsel · ${g.level.label}" else g.level.label + (if (g.number > 0) " #${g.number}" else ""),
                color = Ink, fontSize = 18.sp
            )
            Text(fmtTime(g.seconds), color = Gold, fontSize = 18.sp)
        }
        Box(Modifier.fillMaxWidth()) {
            Board(g)
            if (g.paused) {
                Box(Modifier.matchParentSize().background(Forest).clickable { g.paused = false }, contentAlignment = Alignment.Center) {
                    Text("Pause\nTippen zum Weiterspielen", color = Gold, fontSize = 20.sp, textAlign = TextAlign.Center)
                }
            }
        }
        Text("Fehler: ${g.mistakes}   Tipps: ${g.hints}", color = Muted, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(top = 4.dp), textAlign = TextAlign.Center)
        val h = g.hint
        val note = msg
        if (h != null || note != null) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clip(RoundedCornerShape(14.dp)).background(Panel).border(1.dp, Gold, RoundedCornerShape(14.dp)).padding(10.dp)) {
                Text(h?.text ?: note ?: "", color = Ink, fontSize = 14.sp)
                if (h != null && (h.cell != null || h.elim.isNotEmpty())) {
                    TextButton(onClick = { g.applyHint() }) {
                        Text(if (h.cell != null) "Zahl eintragen" else "Notizen streichen", color = Gold)
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Pad(g, onHint = { msg = null; g.giveHint() }, onCheck = {
            g.hint = null
            val w = g.wrongCount()
            msg = if (w == 0) "Bis jetzt ist alles richtig." else if (w == 1) "Eine Zahl ist falsch (rot markiert)." else "$w Zahlen sind falsch (rot markiert)."
        })
        Spacer(Modifier.height(8.dp))
    }

    if (menu) {
        AlertDialog(
            onDismissRequest = { menu = false }, containerColor = Panel,
            title = { Text("Menü", color = Gold) },
            text = {
                Column {
                    TextButton(onClick = { g.autoNotes(); menu = false }) { Text("Alle Notizen eintragen", color = Ink) }
                    TextButton(onClick = { menu = false; next(g.level) }) { Text("Neues Rätsel (${g.level.label})", color = Ink) }
                    TextButton(onClick = { menu = false; store.saveGame(key, g); home() }) { Text("Zurück zum Menü", color = Ink) }
                }
            },
            confirmButton = { TextButton(onClick = { menu = false }) { Text("Schließen", color = Gold) } }
        )
    }
    if (g.won) {
        AlertDialog(
            onDismissRequest = {}, containerColor = Panel,
            title = { Text("Geschafft! 🎉", color = Gold) },
            text = { Text("Zeit ${fmtTime(g.seconds)} · Fehler ${g.mistakes} · Tipps ${g.hints}", color = Ink) },
            confirmButton = { TextButton(onClick = { next(g.level) }) { Text("Neues Rätsel", color = Gold) } },
            dismissButton = { TextButton(onClick = home) { Text("Menü", color = Ink) } }
        )
    }
}

/** Tastenfeld wie bei den bekannten Sudoku-Apps: links Rückgängig/Wiederholen/Tipp, Mitte 1–9, rechts Notizen/Löschen/Pause. */
@Composable
private fun Pad(g: GameState, onHint: () -> Unit, onCheck: () -> Unit) {
    @Composable
    fun Key(modifier: Modifier = Modifier, on: Boolean = false, enabled: Boolean = true, label: String, sub: String? = null, click: () -> Unit) {
        Box(modifier.height(62.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(54.dp).clip(androidx.compose.foundation.shape.CircleShape).background(if (on) Gold else Color.Transparent)
                    .clickable(enabled = enabled, onClick = click),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label, fontSize = 26.sp, color = if (on) Color(0xFF1A1400) else if (enabled) Ink else Muted.copy(alpha = 0.4f))
                    if (sub != null) Text(sub, fontSize = 9.sp, color = Muted)
                }
            }
        }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        val d = (1..9).toList()
        for (row in 0..2) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val left = listOf(Triple("↶", g.canUndo, { g.undo() }), Triple("↷", g.canRedo, { g.redo() }), Triple("💡", true, onHint))[row]
                Key(Modifier.weight(1f), enabled = left.second, label = left.first) { left.third() }
                for (c in 0..2) {
                    val v = d[row * 3 + c]
                    val rest = 9 - g.count(v)
                    Key(Modifier.weight(1f), on = g.digitFirst && g.hd == v, enabled = rest > 0, label = "$v", sub = if (rest in 1..8) "$rest" else null) { g.tapDigit(v) }
                }
                when (row) {
                    0 -> Key(Modifier.weight(1f), on = g.noteMode, label = "✎") { g.noteMode = !g.noteMode }
                    1 -> Key(Modifier.weight(1f), label = "⌫") { g.erase() }
                    else -> Row(Modifier.weight(1f)) {
                        Key(Modifier.weight(1f), label = "✓") { onCheck() }
                        Key(Modifier.weight(1f), label = "⏸") { g.paused = true }
                    }
                }
            }
        }
    }
}

@Composable
private fun Board(g: GameState) {
    val paint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { textAlign = android.graphics.Paint.Align.CENTER } }
    val hintCells = g.hint?.cells ?: emptyList()
    Canvas(
        Modifier.fillMaxWidth().aspectRatio(1f).background(Panel)
            .pointerInput(Unit) {
                detectTapGestures { o ->
                    val cs = size.width / 9f
                    val c = (o.x / cs).toInt().coerceIn(0, 8); val r = (o.y / cs).toInt().coerceIn(0, 8)
                    g.tapCell(r * 9 + c)
                }
            }
    ) {
        val cs = size.width / 9f
        val sel = g.sel
        val selVal = g.hd
        for (i in 0 until 81) {
            val r = i / 9; val c = i % 9
            val bg = when {
                i == sel -> Gold.copy(alpha = 0.55f)
                i in hintCells -> Gold.copy(alpha = 0.30f)
                selVal != 0 && (g.vals[i] == selVal || (g.vals[i] == 0 && g.notes[i] and (1 shl selVal) != 0)) -> Gold.copy(alpha = 0.35f)
                sel >= 0 && (r == sel / 9 || c == sel % 9 || (r / 3 == sel / 9 / 3 && c / 3 == sel % 9 / 3)) -> Peer
                else -> Color.Transparent
            }
            if (bg != Color.Transparent) drawRect(bg, Offset(c * cs, r * cs), androidx.compose.ui.geometry.Size(cs, cs))
        }
        for (k in 0..9) {
            val thick = k % 3 == 0
            val w = if (thick) 3.dp.toPx() else 1.dp.toPx()
            val col = if (thick) Gold else Color(0x55B8C7B5)
            drawLine(col, Offset(k * cs, 0f), Offset(k * cs, size.height), w)
            drawLine(col, Offset(0f, k * cs), Offset(size.width, k * cs), w)
        }
        val cv = drawContext.canvas.nativeCanvas
        for (i in 0 until 81) {
            val r = i / 9; val c = i % 9
            val v = g.vals[i]
            if (v != 0) {
                paint.textSize = cs * 0.62f
                paint.isFakeBoldText = g.isGiven(i)
                paint.color = when {
                    g.isWrong(i) -> Bad.toArgb()
                    g.isGiven(i) -> Ink.toArgb()
                    else -> Gold.toArgb()
                }
                cv.drawText("$v", c * cs + cs / 2, r * cs + cs / 2 + paint.textSize * 0.35f, paint)
            } else if (g.notes[i] != 0) {
                paint.textSize = cs * 0.27f
                paint.isFakeBoldText = false
                paint.color = Muted.toArgb()
                for (d in 1..9) if (g.notes[i] and (1 shl d) != 0) {
                    val nx = c * cs + cs / 6f * (1 + 2 * ((d - 1) % 3))
                    val ny = r * cs + cs / 6f * (1 + 2 * ((d - 1) / 3)) + paint.textSize * 0.35f
                    cv.drawText("$d", nx, ny, paint)
                }
            }
        }
    }
}
