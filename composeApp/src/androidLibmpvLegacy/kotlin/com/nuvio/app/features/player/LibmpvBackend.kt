package com.nuvio.app.features.player

import `is`.xyz.mpv.BaseMPVView
import `is`.xyz.mpv.MPV

/**
 * Avix: legacy libmpv backend (io.github.abdallahmehiz:mpv-android-lib 0.1.12), only built with
 * -Pavix.legacyLibmpv=true while the mpvRx engine is being validated.
 */
internal typealias MpvHandle = MPV
internal typealias MpvEventObserver = MPV.EventObserver
internal typealias MpvLogObserver = MPV.LogObserver

internal val BaseMPVView.mpvLib: MpvHandle
    get() = mpv

internal object MpvConstants {
    val EVENT_START_FILE: Int get() = MPV.mpvEvent.MPV_EVENT_START_FILE
    val EVENT_END_FILE: Int get() = MPV.mpvEvent.MPV_EVENT_END_FILE
    val EVENT_FILE_LOADED: Int get() = MPV.mpvEvent.MPV_EVENT_FILE_LOADED
    val EVENT_PLAYBACK_RESTART: Int get() = MPV.mpvEvent.MPV_EVENT_PLAYBACK_RESTART
    val FORMAT_STRING: Int get() = MPV.mpvFormat.MPV_FORMAT_STRING
    val FORMAT_FLAG: Int get() = MPV.mpvFormat.MPV_FORMAT_FLAG
    val FORMAT_INT64: Int get() = MPV.mpvFormat.MPV_FORMAT_INT64
    val FORMAT_DOUBLE: Int get() = MPV.mpvFormat.MPV_FORMAT_DOUBLE
    val FORMAT_NODE: Int get() = MPV.mpvFormat.MPV_FORMAT_NODE
}

internal const val LIBMPV_IS_SINGLETON = false
internal const val LIBMPV_BACKEND_NAME = "mpv-android-lib 0.1.12 (legacy)"
