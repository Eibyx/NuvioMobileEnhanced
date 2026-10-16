package com.nuvio.app.features.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

@Composable
internal actual fun rememberPlayerDeviceStatus(): PlayerDeviceStatus {
    val context = LocalContext.current.applicationContext
    var battery by remember(context) { mutableStateOf<Intent?>(null) }
    var network by remember(context) { mutableStateOf(readNetworkType(context)) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                battery = intent
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        // ACTION_BATTERY_CHANGED is sticky: registering returns the current state immediately.
        battery = if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    LaunchedEffect(context) {
        while (true) {
            network = readNetworkType(context)
            delay(NETWORK_REFRESH_MS)
        }
    }

    return PlayerDeviceStatus(
        batteryPercent = batteryPercent(battery),
        batteryCharging = isCharging(battery),
        networkType = network,
    )
}

private fun batteryPercent(intent: Intent?): Int? {
    intent ?: return null
    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    if (level < 0 || scale <= 0) return null
    return (level * 100f / scale).roundToInt().coerceIn(0, 100)
}

private fun isCharging(intent: Intent?): Boolean =
    when (intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
        BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_STATUS_FULL -> true
        else -> false
    }

private fun readNetworkType(context: Context): PlayerDeviceNetworkType = runCatching {
    val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return PlayerDeviceNetworkType.Unknown
    val network = connectivity.activeNetwork ?: return PlayerDeviceNetworkType.Offline
    val capabilities = connectivity.getNetworkCapabilities(network) ?: return PlayerDeviceNetworkType.Unknown
    when {
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> PlayerDeviceNetworkType.Wifi
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> PlayerDeviceNetworkType.Cellular
        else -> PlayerDeviceNetworkType.Unknown
    }
}.getOrDefault(PlayerDeviceNetworkType.Unknown)

private const val NETWORK_REFRESH_MS = 10_000L
