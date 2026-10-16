package com.nuvio.app.features.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Avix (Yesnt10 port 6b5011e0): optional pull-to-refresh on Home with a haptic tick.
 * Off by default because pulling at the top otherwise stretches the hero artwork.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AvixHomePullToRefresh(
    enabled: Boolean,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        content()
        return
    }
    val haptics = LocalHapticFeedback.current
    var manualRefresh by remember { mutableStateOf(false) }
    // The spinner shows only for refreshes the user started, not for background reloads.
    LaunchedEffect(isLoading) {
        if (!isLoading) manualRefresh = false
    }
    PullToRefreshBox(
        isRefreshing = manualRefresh && isLoading,
        onRefresh = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            manualRefresh = true
            onRefresh()
        },
        modifier = Modifier.fillMaxSize(),
    ) {
        content()
    }
}
