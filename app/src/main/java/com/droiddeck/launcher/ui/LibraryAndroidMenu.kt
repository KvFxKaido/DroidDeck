package com.droiddeck.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.droiddeck.launcher.HomeApp
import com.droiddeck.launcher.R

@Composable
internal fun AndroidEntryDialog(app: HomeApp.LaunchableApp, favorite: Boolean, onFavorite: () -> Unit, onRemove: () -> Unit, onArt: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(app.label) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onFavorite, modifier = Modifier.focusGlide(CircleShape)) {
                    Text(stringResource(if (favorite) R.string.fm_favorite_remove else R.string.fm_favorite_add))
                }
                TextButton(onClick = onArt, modifier = Modifier.focusGlide(CircleShape)) { Text(stringResource(R.string.library_art_change)) }
                TextButton(onClick = onRemove, modifier = Modifier.focusGlide(CircleShape)) { Text(stringResource(R.string.library_android_remove)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.focusGlide(CircleShape)) { Text(stringResource(R.string.common_cancel)) } },
    )
}

// For games from DroidDeck's own emulators: they are found automatically, so there is nothing to remove.
@Composable
internal fun DeckRomDialog(title: String, favorite: Boolean, onFavorite: () -> Unit, onArt: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onFavorite, modifier = Modifier.focusGlide(CircleShape)) {
                    Text(stringResource(if (favorite) R.string.fm_favorite_remove else R.string.fm_favorite_add))
                }
                TextButton(onClick = onArt, modifier = Modifier.focusGlide(CircleShape)) { Text(stringResource(R.string.library_art_change)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.focusGlide(CircleShape)) { Text(stringResource(R.string.common_cancel)) } },
    )
}
