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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
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

class Pal(
    val name: String, val bg: Color, val panel: Color, val peer: Color, val ink: Color, val muted: Color,
    val accent: Color, val onAccent: Color, val line: Color, val bad: Color, val light: Boolean,
    val tiles: Boolean = false, val boardBg: Color = Color.Unspecified, val tileGiven: Color = Color.Unspecified, val tileEmpty: Color = Color.Unspecified, val userInk: Color = Color.Unspecified
)

object Palettes {
    val all = listOf(
        Pal("Wald", Color(0xFF0B2A16), Color(0xFF12381F), Color(0xFF1B4A2A), Color(0xFFF2F0E0), Color(0xFFD3DFD0), Color(0xFFE0B23E), OnAccent, Color(0xFFF2C94C), Color(0xFFFF6B5E), false),
        Pal("Helles Holz", Color(0xFFF0DDB9), Color(0xFFE4C896), Color(0xFFEBD3A6), Color(0xFF2E1D0B), Color(0xFF5A4023), Color(0xFFA85F0C), Color(0xFFFFF8E8), Color(0xFF8A5A1F), Color(0xFFC2260F), true),
        Pal("Nacht", Color(0xFF0E1A33), Color(0xFF172949), Color(0xFF20365E), Color(0xFFEEF2FB), Color(0xFFC3CEE6), Color(0xFFF2C94C), OnAccent, Color(0xFFF2C94C), Color(0xFFFF7A6B), false),
        Pal("Papier", Color(0xFFFAF9F2), Color(0xFFECEADD), Color(0xFFE2E7D8), Color(0xFF1B1B1B), Color(0xFF50504A), Color(0xFF1B6EB5), Color(0xFFFFFFFF), Color(0xFFB08A1E), Color(0xFFC2260F), true),
        Pal(
            "Holzbrett", Color(0xFFC9A87C), Color(0xFFE6CC9A), Color(0xFFDDBF88), Color(0xFF5A3318), Color(0xFF6B4524), Color(0xFF8A4B12), Color(0xFFFFF8E8),
            Color(0xFF3B2412), Color(0xFFC2260F), true,
            tiles = true, boardBg = Color(0xFF4A2E1B), tileGiven = Color(0xFFF0D9A9), tileEmpty = Color(0xFFE6CB96), userInk = Color(0xFF9A6B3C)
        )
    )
    var cur by mutableStateOf(all[0])
}

val Forest: Color get() = Palettes.cur.bg
val Panel: Color get() = Palettes.cur.panel
val Peer: Color get() = Palettes.cur.peer
val Gold: Color get() = Palettes.cur.accent
val Ink: Color get() = Palettes.cur.ink
val Muted: Color get() = Palettes.cur.muted
val Bad: Color get() = Palettes.cur.bad
val OnAccent: Color get() = Palettes.cur.onAccent

@Composable
fun SudoneaTheme(content: @Composable () -> Unit) {
    val p = Palettes.cur
    val scheme = if (p.light) lightColorScheme(primary = p.accent, onPrimary = p.onAccent, background = p.bg, surface = p.panel, onSurface = p.ink, onBackground = p.ink, secondary = p.accent)
    else darkColorScheme(primary = p.accent, onPrimary = p.onAccent, background = p.bg, surface = p.panel, onSurface = p.ink, onBackground = p.ink, secondary = p.accent)
    MaterialTheme(colorScheme = scheme, content = content)
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
    var musicOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val light = Palettes.cur.light
    SideEffect {
        (view.context as? android.app.Activity)?.window?.let {
            val c = androidx.core.view.WindowCompat.getInsetsController(it, view)
            c.isAppearanceLightStatusBars = light
            c.isAppearanceLightNavigationBars = light
        }
    }

    fun resume(g: GameState) { screen = Screen.Play(g, g.id) }

    fun start(level: Level, daily: String?) {
        if (daily != null) {
            val saved = store.loadGame("daily-$daily")
            if (saved != null) { resume(saved); return }
        }
        busy = true
        scope.launch {
            val number = if (daily == null) (1..99999).random() else 0
            val p = withContext(Dispatchers.Default) {
                if (daily == null) Sudoku.generate(level, java.util.Random(number.toLong() * 31 + level.ordinal))
                else { val d = LocalDate.parse(daily); Sudoku.daily(d.year, d.monthValue, d.dayOfMonth, d.dayOfWeek.value) }
            }
            val g = GameState.of(p, daily, number)
            store.started(g.level)
            store.saveGame(g.id, g)
            busy = false
            resume(g)
        }
    }

    Column(Modifier.fillMaxSize().background(Forest).statusBarsPadding()) {
        Box(Modifier.weight(1f)) {
            when (val s = screen) {
                Screen.Home -> HomeScreen(store, busy, ::start, ::resume, { screen = Screen.Stats }, { screen = Screen.Info }, { musicOpen = true })
                Screen.Stats -> StatsScreen(store) { screen = Screen.Home }
                Screen.Info -> InfoScreen(ads) { screen = Screen.Home }
                is Screen.Play -> GameScreen(s.game, store, s.key, { screen = Screen.Home }, { lv -> screen = Screen.Home; start(lv, null) }, { musicOpen = true })
            }
        }
        BannerBar(ads)
        Spacer(Modifier.navigationBarsPadding())
    }
    if (musicOpen) MusicDialog { musicOpen = false }
}

@Composable
private fun GoldButton(text: String, sub: String? = null, onClick: () -> Unit) {
    Button(
        onClick = onClick, modifier = Modifier.fillMaxWidth().height(60.dp), shape = RoundedCornerShape(30.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = OnAccent)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (sub != null) Text(sub, fontSize = 12.sp)
        }
    }
}

@Composable
fun HomeScreen(store: Store, busy: Boolean, start: (Level, String?) -> Unit, resume: (GameState) -> Unit, stats: () -> Unit, info: () -> Unit, music: () -> Unit) {
    val today = LocalDate.now().toString()
    val done = store.dailyDone(today)
    val streak = store.streak()
    var refresh by remember { mutableStateOf(0) }
    var del by remember { mutableStateOf<GameState?>(null) }
    val open = remember(refresh) { store.gameIds().mapNotNull { store.loadGame(it) } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Image(painterResource(R.drawable.logo_header), null, Modifier.fillMaxWidth().height(130.dp), contentScale = ContentScale.Fit)
        if (busy) Text("Rätsel wird erstellt …", color = Gold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        GoldButton(
            "★ Tagesrätsel", if (done) "heute gelöst ✓" + (if (streak > 0) " · Serie $streak" else "")
            else "für alle gleich" + (if (streak > 0) " · Serie $streak" else "")
        ) { start(Level.EASY, today) }
        Level.values().forEach { l ->
            OutlinedButton(
                onClick = { start(l, null) }, modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(26.dp), border = androidx.compose.foundation.BorderStroke(1.5.dp, Gold)
            ) { Text(l.label + "  ·  neues Rätsel", color = Ink, fontSize = 16.sp) }
        }
        if (open.isNotEmpty()) {
            Text("Angefangene Rätsel", color = Gold, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(top = 8.dp))
            open.forEach { g ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel).clickable { resume(g) }.padding(start = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                        Text(if (g.daily != null) "★ Tagesrätsel ${g.daily}" else g.level.label + " #" + g.number, color = Ink, fontWeight = FontWeight.Bold)
                        Text("${fmtTime(g.seconds)} · ${(0 until 81).count { g.vals[it] != 0 && !g.isGiven(it) }} von ${81 - g.grid.count { it != 0 }} Feldern", color = Muted, fontSize = 12.sp)
                    }
                    TextButton(onClick = { del = g }) { Text("🗑", fontSize = 20.sp) }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            TextButton(onClick = stats) { Text("Statistik & Verlauf", color = Gold) }
            TextButton(onClick = info) { Text("Info & Datenschutz", color = Gold) }
        }
        OutlinedButton(onClick = music, modifier = Modifier.fillMaxWidth()) { Text("🎵 Musik", color = Ink) }
    }
    del?.let { g ->
        AlertDialog(
            onDismissRequest = { del = null }, containerColor = Panel,
            title = { Text("Rätsel löschen?", color = Gold) },
            text = { Text((if (g.daily != null) "Tagesrätsel ${g.daily}" else g.level.label + " #" + g.number) + " wird mit dem Spielstand entfernt.", color = Ink) },
            confirmButton = { TextButton(onClick = { store.deleteGame(g.id); del = null; refresh++ }) { Text("Löschen", color = Bad) } },
            dismissButton = { TextButton(onClick = { del = null }) { Text("Abbrechen", color = Ink) } }
        )
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
        var refresh by remember { mutableStateOf(0) }
        val hist = remember(refresh) { store.history() }
        Text("Bearbeitete Rätsel", color = Gold, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(top = 8.dp))
        if (hist.isEmpty()) Text("Noch keine gelösten Rätsel.", color = Muted)
        hist.forEachIndexed { i, h ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel).padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                    Text((if (h.daily != null) "★ Tagesrätsel ${h.daily}" else (h.level?.label ?: "") + " #" + h.number), color = Ink, fontWeight = FontWeight.Bold)
                    Text("${h.date} · ${fmtTime(h.seconds)} · Fehler ${h.mistakes} · Tipps ${h.hints}", color = Muted, fontSize = 12.sp)
                }
                TextButton(onClick = { store.deleteHistory(i); refresh++ }) { Text("🗑", fontSize = 20.sp) }
            }
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
fun GameScreen(g: GameState, store: Store, key: String, home: () -> Unit, next: (Level) -> Unit, music: () -> Unit) {
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
        Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { store.saveGame(key, g); home() }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("←", color = Gold, fontSize = 24.sp) }
            Text(
                if (g.daily != null) "★ Tagesrätsel" else g.level.label + (if (g.number > 0) " #${g.number}" else ""),
                color = Ink, fontSize = 17.sp, modifier = Modifier.weight(1f), maxLines = 1
            )
            Text(fmtTime(g.seconds), color = Gold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = { menu = true }, contentPadding = PaddingValues(horizontal = 12.dp)) { Text("⋮", color = Gold, fontSize = 24.sp) }
        }
        Box(Modifier.fillMaxWidth()) {
            Board(g)
            if (g.paused) {
                Box(Modifier.matchParentSize().background(Forest).clickable { g.paused = false }, contentAlignment = Alignment.Center) {
                    Text("Pause\nTippen zum Weiterspielen", color = Gold, fontSize = 20.sp, textAlign = TextAlign.Center)
                }
            }
        }
        val h = g.hint
        val note = msg
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.Top) {
            Column {
                Text("Fehler: ${g.mistakes}", color = Ink, fontSize = 14.sp)
                Text("Tipps: ${g.hints}", color = Ink, fontSize = 14.sp)
            }
            if (h != null || note != null) {
                Column(
                    Modifier.weight(1f).padding(start = 12.dp).heightIn(max = 110.dp).clip(RoundedCornerShape(12.dp)).background(Panel)
                        .border(1.dp, Gold, RoundedCornerShape(12.dp)).verticalScroll(rememberScrollState()).padding(8.dp)
                ) {
                    Text(h?.text ?: note ?: "", color = Ink, fontSize = 13.sp)
                    if (h != null && (h.cell != null || h.elim.isNotEmpty())) {
                        Text(
                            if (h.cell != null) "▶ Zahl eintragen" else "▶ Notizen streichen", color = Gold, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 6.dp).clickable { g.applyHint() }
                        )
                    }
                }
            }
        }
        Pad(g, onHint = { msg = null; g.giveHint() }, onCheck = {
            g.hint = null
            val w = g.wrongCount()
            msg = if (w == 0) "Bis jetzt ist alles richtig." else if (w == 1) "Eine Zahl ist falsch (rot markiert)." else "$w Zahlen sind falsch (rot markiert)."
        })
        Spacer(Modifier.weight(1f))
    }

    if (menu) {
        AlertDialog(
            onDismissRequest = { menu = false }, containerColor = Panel,
            title = { Text("Menü", color = Gold) },
            text = {
                Column {
                    Text("Farbe", color = Muted, fontSize = 12.sp)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Palettes.all.forEachIndexed { i, p ->
                            Box(
                                Modifier.clip(RoundedCornerShape(10.dp)).background(p.bg).border(2.dp, if (Palettes.cur === p) Gold else Color.Gray, RoundedCornerShape(10.dp))
                                    .clickable { Palettes.cur = p; store.setTheme(i) }.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) { Text(p.name, color = p.ink, fontSize = 13.sp) }
                        }
                    }
                    TextButton(onClick = { Sfx.set(!Sfx.on) }) { Text(if (Sfx.on) "🔊 Tastentöne: an" else "🔇 Tastentöne: aus", color = Ink) }
                    TextButton(onClick = { menu = false; music() }) { Text("🎵 Musik", color = Ink) }
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
                    .clickable(enabled = enabled, onClick = { Sfx.click(); click() }),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label, fontSize = 26.sp, color = if (on) OnAccent else if (enabled) Ink else Muted.copy(alpha = 0.4f))
                    if (sub != null) Text(sub, fontSize = 11.sp, color = Ink, fontWeight = FontWeight.Bold)
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
        Modifier.fillMaxWidth().aspectRatio(1f).background(if (Palettes.cur.tiles) Palettes.cur.boardBg else Panel)
            .pointerInput(Unit) {
                detectTapGestures { o ->
                    val cs = size.width / 9f
                    val c = (o.x / cs).toInt().coerceIn(0, 8); val r = (o.y / cs).toInt().coerceIn(0, 8)
                    Sfx.click()
                    g.tapCell(r * 9 + c)
                }
            }
    ) {
        val cs = size.width / 9f
        val sel = g.sel
        val selVal = g.hd
        val pal = Palettes.cur
        for (i in 0 until 81) {
            val r = i / 9; val c = i % 9
            val bg = when {
                i == sel -> Gold.copy(alpha = 0.55f)
                i in hintCells -> Gold.copy(alpha = 0.30f)
                selVal != 0 && (g.vals[i] == selVal || (g.vals[i] == 0 && g.notes[i] and (1 shl selVal) != 0)) -> Gold.copy(alpha = 0.35f)
                sel >= 0 && (r == sel / 9 || c == sel % 9 || (r / 3 == sel / 9 / 3 && c / 3 == sel % 9 / 3)) -> Peer.copy(alpha = if (pal.tiles) 0.55f else 1f)
                else -> Color.Transparent
            }
            if (pal.tiles) {
                // Holzbrett: erhabene Steine (Vorgaben) und vertiefte Felder, größere Fuge zwischen den Blöcken
                val small = cs * 0.045f; val big = cs * 0.10f
                val l = c * cs + (if (c % 3 == 0) big else small); val rt = (c + 1) * cs - (if (c % 3 == 2) big else small)
                val t = r * cs + (if (r % 3 == 0) big else small); val bt = (r + 1) * cs - (if (r % 3 == 2) big else small)
                val tl = Offset(l, t); val sz = androidx.compose.ui.geometry.Size(rt - l, bt - t)
                val rad = androidx.compose.ui.geometry.CornerRadius(cs * 0.12f)
                val given = g.isGiven(i)
                drawRoundRect(if (given) pal.tileGiven else pal.tileEmpty, tl, sz, rad)
                drawRoundRect(pal.line.copy(alpha = if (given) 0.85f else 0.45f), tl, sz, rad, style = androidx.compose.ui.graphics.drawscope.Stroke(if (given) 1.6.dp.toPx() else 1.dp.toPx()))
                if (bg != Color.Transparent) drawRoundRect(bg, tl, sz, rad)
            } else if (bg != Color.Transparent) drawRect(bg, Offset(c * cs, r * cs), androidx.compose.ui.geometry.Size(cs, cs))
        }
        if (!pal.tiles) for (k in 0..9) {
            val thick = k % 3 == 0
            val w = if (thick) 2.dp.toPx() else 0.6.dp.toPx()
            val col = if (thick) pal.line else pal.muted.copy(alpha = 0.45f)
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
                    pal.tiles -> pal.userInk.toArgb()
                    else -> Gold.toArgb()
                }
                cv.drawText("$v", c * cs + cs / 2, r * cs + cs / 2 + paint.textSize * 0.35f, paint)
            } else if (g.notes[i] != 0) {
                paint.textSize = cs * 0.30f
                paint.isFakeBoldText = true
                paint.color = Ink.toArgb()
                for (d in 1..9) if (g.notes[i] and (1 shl d) != 0) {
                    val nx = c * cs + cs / 6f * (1 + 2 * ((d - 1) % 3))
                    val ny = r * cs + cs / 6f * (1 + 2 * ((d - 1) / 3)) + paint.textSize * 0.35f
                    cv.drawText("$d", nx, ny, paint)
                }
            }
        }
    }
}


/** Wird nach einem Absturz einmal gezeigt, damit der Fehler gemeldet werden kann. */
@Composable
fun CrashScreen(trace: String, proceed: () -> Unit) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().background(Forest).statusBarsPadding().navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Die App ist beim letzten Mal abgestürzt", color = Gold, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("Bitte teile den Fehlerbericht, dann behebe ich es.", color = Ink)
        Text(trace, color = Muted, fontSize = 10.sp, modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()))
        Button(onClick = {
            ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, trace), "Fehlerbericht teilen"))
        }, modifier = Modifier.fillMaxWidth()) { Text("Fehlerbericht teilen") }
        OutlinedButton(onClick = proceed, modifier = Modifier.fillMaxWidth()) { Text("Weiter zur App", color = Ink) }
    }
}
