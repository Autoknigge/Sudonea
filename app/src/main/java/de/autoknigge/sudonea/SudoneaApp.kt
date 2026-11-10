package de.autoknigge.sudonea

import android.app.Application

/** Merkt sich den Fehlertext eines Absturzes, damit die App ihn beim nächsten Start anzeigen kann. */
class SudoneaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val old = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                getSharedPreferences("crash", MODE_PRIVATE).edit()
                    .putString("trace", android.util.Log.getStackTraceString(e).take(3500)).commit()
            } catch (_: Throwable) {
            }
            old?.uncaughtException(t, e)
        }
    }
}
