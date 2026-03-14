package com.vibez.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibez.api.VibezApi
import com.vibez.model.GeneratePlaylistRequest
import com.vibez.model.PlaylistResult
import kotlinx.coroutines.launch

class HomeViewModel(
    private val api: VibezApi = VibezApi()
) : ViewModel() {
    var vibeText by mutableStateOf("")
    var platform by mutableStateOf("spotify")
    var loading by mutableStateOf(false)
    var playlist by mutableStateOf<PlaylistResult?>(null)
    var error by mutableStateOf<String?>(null)

    fun generatePlaylist() {
        if (vibeText.isBlank() || loading) return

        viewModelScope.launch {
            loading = true
            error = null
            try {
                playlist = api.generatePlaylist(
                    GeneratePlaylistRequest(
                        userId = "00000000-0000-0000-0000-000000000001",
                        platform = platform,
                        vibeText = vibeText
                    )
                )
            } catch (e: Exception) {
                error = e.message ?: "Failed to generate playlist"
            } finally {
                loading = false
            }
        }
    }

    fun reset() {
        playlist = null
    }
}
