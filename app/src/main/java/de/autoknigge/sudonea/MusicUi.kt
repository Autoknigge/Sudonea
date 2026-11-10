package de.autoknigge.sudonea

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Musik aus eigenen Dateien. Läuft über den Hintergrunddienst weiter, wenn eine andere App geöffnet wird. */
@Composable
fun MusicDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val music = remember { MusicHub.get(ctx) }
    var tick by remember { mutableIntStateOf(0) }
    var vol by remember { mutableFloatStateOf(music.volume) }
    DisposableEffect(music) {
        val l: () -> Unit = { tick++ }
        music.addListener(l)
        onDispose { music.removeListener(l) }
    }
    val askNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val prefs = remember { ctx.getSharedPreferences("sudonea_music", 0) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            if (android.os.Build.VERSION.SDK_INT >= 33 &&
                ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED &&
                !prefs.getBoolean("asked_notifications", false)
            ) {
                prefs.edit().putBoolean("asked_notifications", true).apply()
                askNotif.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
            music.add(uris)
        }
    }
    @Suppress("UNUSED_VARIABLE") val t = tick
    val cur = music.current()
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Panel,
        title = { Text("🎵 Musik", color = Gold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (cur == null) "Noch keine Titel gewählt" else (if (music.playing) "▶  " else "⏸  ") + cur.name,
                    color = Ink, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { music.previous() }) { Text("⏮", fontSize = 26.sp, color = Ink) }
                    TextButton(onClick = { music.toggle() }) { Text(if (music.playing) "⏸" else "▶", fontSize = 30.sp, color = Gold) }
                    TextButton(onClick = { music.next() }) { Text("⏭", fontSize = 26.sp, color = Ink) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { music.shuffle = !music.shuffle }, modifier = Modifier.weight(1f)) {
                        Text(if (music.shuffle) "🔀 Zufall: an" else "🔀 Zufall: aus", color = Ink, fontSize = 12.sp)
                    }
                    OutlinedButton(onClick = { music.repeat = !music.repeat }, modifier = Modifier.weight(1f)) {
                        Text(if (music.repeat) "🔁 Wiederholen: an" else "🔁 Wiederholen: aus", color = Ink, fontSize = 12.sp)
                    }
                }
                Text("Lautstärke der Musik", color = Muted, fontSize = 12.sp)
                Slider(value = vol, onValueChange = { vol = it; music.volume = it })
                Column(Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState())) {
                    music.tracks.forEachIndexed { i, tr ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                tr.name, color = if (i == music.index) Gold else Ink, fontWeight = if (i == music.index) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).clickable { music.play(i) }.padding(vertical = 8.dp)
                            )
                            TextButton(onClick = { music.remove(i) }) { Text("✕", color = Muted) }
                        }
                    }
                }
                OutlinedButton(onClick = { picker.launch(arrayOf("audio/*", "video/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Text("＋ Dateien hinzufügen (Musik oder Video)", color = Ink, fontSize = 13.sp)
                }
                Text("Es läuft nur der Ton. Die Musik läuft weiter, wenn du eine andere App öffnest. In der Benachrichtigung steuerst du sie.", color = Muted, fontSize = 11.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fertig", color = Gold) } },
        dismissButton = { TextButton(onClick = { music.clear() }) { Text("Liste leeren", color = Ink) } }
    )
}
