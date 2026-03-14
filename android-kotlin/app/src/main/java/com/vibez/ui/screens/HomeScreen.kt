package com.vibez.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vibez.ui.components.VibeInputField
import com.vibez.viewmodel.HomeViewModel

@Composable
fun HomeScreen(viewModel: HomeViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Vibez", style = MaterialTheme.typography.headlineMedium)
        VibeInputField(
            value = viewModel.vibeText,
            onValueChange = { viewModel.vibeText = it },
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = { viewModel.platform = "spotify" }, modifier = Modifier.fillMaxWidth()) {
            Text("Create Playlist for Spotify")
        }
        Button(onClick = { viewModel.platform = "youtube" }, modifier = Modifier.fillMaxWidth()) {
            Text("Create Playlist for YouTube")
        }
        Button(onClick = { viewModel.platform = "soundcloud" }, modifier = Modifier.fillMaxWidth()) {
            Text("Create Playlist for SoundCloud")
        }
        Button(onClick = { viewModel.platform = "deezer" }, modifier = Modifier.fillMaxWidth()) {
            Text("Create Playlist for Deezer")
        }
        Button(onClick = viewModel::generatePlaylist, modifier = Modifier.fillMaxWidth()) {
            Text(if (viewModel.loading) "Generating..." else "Generate Playlist")
        }
        viewModel.error?.let { Text("Error: $it") }
    }
}
