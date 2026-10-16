package com.nuvio.app.core.ui

/** iOS v1: the status bar follows the system; the option is hidden there. */
internal actual object AvixSystemUiController {
    actual val isSupported: Boolean = false
    actual fun setStatusBarHidden(hidden: Boolean) = Unit
    actual fun reapply() = Unit
}
