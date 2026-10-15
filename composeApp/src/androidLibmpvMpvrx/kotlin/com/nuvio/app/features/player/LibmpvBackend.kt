package com.nuvio.app.features.player

import `is`.xyz.mpv.BaseMPVView
import `is`.xyz.mpv.MPVLib

/**
 * Avix: libmpv backend = mpvRx engine (app.gyrolet.mpvlib:mpvlib, see docs/MPVRX_INTEGRATION.md).
 * The selection between this file and the legacy one is made by Gradle (-Pavix.legacyLibmpv).
 *
 * MPVLib is a process-wide singleton: only one libmpv core may exist at a time, which is enforced
 * by [LibmpvInstanceGuard].
 */
internal typealias MpvHandle = MPVLib
internal typealias MpvEventObserver = MPVLib.EventObserver
internal typealias MpvLogObserver = MPVLib.LogObserver

internal val BaseMPVView.mpvLib: MpvHandle
    get() = MPVLib

internal object MpvConstants {
    val EVENT_START_FILE: Int get() = MPVLib.MpvEvent.MPV_EVENT_START_FILE
    val EVENT_END_FILE: Int get() = MPVLib.MpvEvent.MPV_EVENT_END_FILE
    val EVENT_FILE_LOADED: Int get() = MPVLib.MpvEvent.MPV_EVENT_FILE_LOADED
    val EVENT_PLAYBACK_RESTART: Int get() = MPVLib.MpvEvent.MPV_EVENT_PLAYBACK_RESTART
    val FORMAT_STRING: Int get() = MPVLib.MpvFormat.MPV_FORMAT_STRING
    val FORMAT_FLAG: Int get() = MPVLib.MpvFormat.MPV_FORMAT_FLAG
    val FORMAT_INT64: Int get() = MPVLib.MpvFormat.MPV_FORMAT_INT64
    val FORMAT_DOUBLE: Int get() = MPVLib.MpvFormat.MPV_FORMAT_DOUBLE
    val FORMAT_NODE: Int get() = MPVLib.MpvFormat.MPV_FORMAT_NODE
}

internal const val LIBMPV_IS_SINGLETON = true
internal const val LIBMPV_BACKEND_NAME = "mpvRx (mpvlib 1.1.3)"
