package de.autoknigge.sudonea

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    private lateinit var ads: AdsController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        ads = AdsController(this)
        val store = Store(this)
        Palettes.cur = Palettes.all.getOrElse(store.theme()) { Palettes.all[0] }
        val sp = getSharedPreferences("crash", MODE_PRIVATE)
        val crash = sp.getString("trace", null)
        setContent {
            SudoneaTheme {
                val c = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(crash) }
                val t = c.value
                if (t != null) CrashScreen(t) { sp.edit().remove("trace").commit(); c.value = null }
                else Root(store, ads)
            }
        }
        ads.start()
    }
}
