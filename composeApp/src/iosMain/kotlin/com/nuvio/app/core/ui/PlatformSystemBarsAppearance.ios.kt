package com.nuvio.app.core.ui

import androidx.compose.runtime.Composable

// iOS is not part of Avix v1; the status bar keeps the app's existing style.
@Composable
actual fun PlatformSystemBarsAppearance(darkTheme: Boolean) = Unit
