package com.vibez.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vibez.model.PlaylistResult

@Composable
fun PlaylistPreviewScreen(playlist: PlaylistResult, onReset: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(playlist.playlistName)
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(playlist.tracks) { track ->
                Text("${track.title} - ${track.artist}", modifier = Modifier.padding(vertical = 6.dp))
            }
        }
        Button(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
            Text("Regenerate Playlist")
        }
    }
}
