package com.droiddeck.launcher.ui

import android.app.WallpaperManager
import android.graphics.drawable.ColorDrawable
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droiddeck.launcher.core.AppUiPrefs

internal object ShelfState {
    var showing by mutableStateOf(false)
}

@Composable
internal fun ShelfSpike(navigate: String?, onNavigated: () -> Unit) {
    val view = LocalView.current
    val activity = remember(view) { view.context.findActivity() }
    DisposableEffect(activity) {
        val window = activity?.window
        val before = window?.decorView?.background
        val format = window?.attributes?.format
        // An opaque surface keeps the last front-end frame where nothing is drawn, so the wallpaper never shows.
        window?.setFormat(android.graphics.PixelFormat.TRANSLUCENT)
        window?.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            window?.setBackgroundDrawable(before ?: ColorDrawable(android.graphics.Color.BLACK))
            if (format != null) window.setFormat(format)
        }
    }
    LaunchedEffect(navigate) {
        when (navigate) {
            null -> Unit
            AppUiPrefs.HOME_PAGE_SHELF -> onNavigated()
            else -> ShelfState.showing = false
        }
    }
    BackHandler { ShelfState.showing = false }
    val wallpaper = remember(activity) {
        runCatching {
            WallpaperManager.getInstance(view.context).wallpaperInfo?.let { "${it.packageName} / ${it.serviceName}" } ?: "none (static image or no live wallpaper)"
        }.getOrElse { "could not read (${it.javaClass.simpleName})" }
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                repeat(6) { Box(Modifier.size(110.dp).clip(RoundedCornerShape(14.dp)).background(Color(0x55FFFFFF))) }
            }
            Text("Shelf wallpaper spike", color = Color.White, fontSize = 22.sp)
            Text("Live wallpaper: $wallpaper", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
            Button(onClick = { ShelfState.showing = false }, modifier = Modifier.focusRequester(focus)) { Text("Library") }
        }
    }
}
