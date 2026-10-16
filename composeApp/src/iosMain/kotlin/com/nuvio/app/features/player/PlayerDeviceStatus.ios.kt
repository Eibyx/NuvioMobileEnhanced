package com.nuvio.app.features.player

import androidx.compose.runtime.Composable

/** iOS: clock and end time only (battery and network are not read). */
@Composable
internal actual fun rememberPlayerDeviceStatus(): PlayerDeviceStatus = PlayerDeviceStatus()
