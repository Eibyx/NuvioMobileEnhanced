package com.nuvio.app.core.ui

import android.app.Activity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.lang.ref.WeakReference

internal actual object AvixSystemUiController {
    private var activity: WeakReference<Activity>? = null
    private var hidden = false

    actual val isSupported: Boolean = true

    fun bind(activity: Activity) {
        this.activity = WeakReference(activity)
        AvixAppearancePreferences.ensureLoaded()
        hidden = AvixAppearancePreferences.statusBarHidden.value
        reapply()
    }

    actual fun setStatusBarHidden(hidden: Boolean) {
        this.hidden = hidden
        reapply()
    }

    actual fun reapply() {
        val window = activity?.get()?.window ?: return
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (hidden) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.statusBars())
        } else {
            controller.show(WindowInsetsCompat.Type.statusBars())
        }
    }
}
