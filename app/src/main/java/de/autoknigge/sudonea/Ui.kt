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
            val p = withContext(Dispatchers.Default) {
                if (daily == null) Sudoku.generate(level)
                else { val d = LocalDate.parse(daily); Sudoku.daily(d.year, d.monthValue, d.dayOfMonth, d.dayOfWeek.value) }
            }
            val g = GameState.of(p, daily)
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
    LaunchedEffect(g) {
        var n = 0
        while (!g.won) {
            delay(1000); g.seconds++
            if (++n % 10 == 0) store.saveGame(key, g)
        }
    }
    LaunchedEffect(g.won) {
        if (g.won) { store.finished(g); store.saveGame(key, null) }
    }
    // Spielstand sichern, sobald sich etwas ändert
    LaunchedEffect(g.vals.toList(), g.notes.toList()) { store.saveGame(key, g) }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { store.saveGame(key, g); home() }) { Text("‹ Menü", color = Gold) }
            Text((if (g.daily != null) "★ Tagesrätsel · " else "") + g.level.label, color = Ink, fontWeight = FontWeight.Bold)
            Text(fmtTime(g.seconds), color = Gold, fontSize = 18.sp)
        }
        Text("Fehler: ${g.mistakes}   Tipps: ${g.hints}", color = Muted, fontSize = 13.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Board(g)
        val h = g.hint
        if (h != null) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel).border(1.dp, Gold, RoundedCornerShape(14.dp)).padding(12.dp)) {
                Text(h.text, color = Ink, fontSize = 14.sp)
                if (h.cell != null || h.elim.isNotEmpty()) {
                    TextButton(onClick = { g.applyHint() }) {
                        Text(if (h.cell != null) "Zahl eintragen" else "Notizen streichen", color = Gold)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Tool("↶", "Zurück") { g.undo() }
            Tool("⌫", "Löschen") { g.erase() }
            Tool(if (g.noteMode) "✎ an" else "✎", "Notizen", g.noteMode) { g.noteMode = !g.noteMode }
            Tool("💡", "Tipp") { g.giveHint() }
            Tool("▦", "Auto-Notiz") { g.autoNotes() }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (d in 1..9) {
                val left = 9 - g.count(d)
                Column(
                    Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(10.dp)).background(if (left <= 0) Panel else Peer)
                        .clickable(enabled = left > 0) { g.enter(d) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                ) {
                    Text("$d", fontSize = 24.sp, color = if (left <= 0) Muted else Ink, fontWeight = FontWeight.Bold)
                    Text(if (left > 0) "$left" else "", fontSize = 10.sp, color = Muted)
                }
            }
        }
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

@Composable
private fun Tool(icon: String, label: String, on: Boolean = false, click: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).background(if (on) Gold else Panel).clickable(onClick = click).padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(icon, fontSize = 20.sp, color = if (on) Color(0xFF1A1400) else Ink)
        Text(label, fontSize = 10.sp, color = if (on) Color(0xFF1A1400) else Muted)
    }
}

@Composable
private fun Board(g: GameState) {
    val paint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { textAlign = android.graphics.Paint.Align.CENTER } }
    val hintCells = g.hint?.cells ?: emptyList()
    Canvas(
        Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(6.dp)).background(Panel)
            .pointerInput(Unit) {
                detectTapGestures { o ->
                    val cs = size.width / 9f
                    val c = (o.x / cs).toInt().coerceIn(0, 8); val r = (o.y / cs).toInt().coerceIn(0, 8)
                    g.sel = r * 9 + c
                }
            }
    ) {
        val cs = size.width / 9f
        val sel = g.sel
        val selVal = if (sel >= 0) g.vals[sel] else 0
        for (i in 0 until 81) {
            val r = i / 9; val c = i % 9
            val bg = when {
                i == sel -> Gold.copy(alpha = 0.55f)
                i in hintCells -> Gold.copy(alpha = 0.30f)
                selVal != 0 && g.vals[i] == selVal -> SameDigit
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
                paint.textSize = cs * 0.24f
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
