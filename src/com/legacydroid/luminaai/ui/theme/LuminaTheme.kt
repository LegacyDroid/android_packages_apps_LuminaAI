/*
 * SPDX-FileCopyrightText: 2026 The LegacyDroid Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.legacydroid.luminaai.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

// Wallpaper colors on Android 12 and up, plain dark below that.
// The overlay is always dark either way.
@Composable
fun LuminaColorScheme() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    dynamicDarkColorScheme(LocalContext.current)
} else {
    darkColorScheme()
}

@Composable
fun LuminaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LuminaColorScheme()) {
        content()
    }
}