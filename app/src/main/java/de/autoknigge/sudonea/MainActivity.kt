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
        setContent { SudoneaTheme { Root(store, ads) } }
        ads.start()
    }
}
