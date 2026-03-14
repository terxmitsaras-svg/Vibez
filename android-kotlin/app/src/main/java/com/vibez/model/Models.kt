package com.vibez.model

import kotlinx.serialization.Serializable

@Serializable
data class GeneratePlaylistRequest(
    val userId: String,
    val platform: String,
    val vibeText: String,
    val targetTrackCount: Int = 30
)

@Serializable
data class Track(
    val title: String,
    val artist: String,
    val platform: String,
    val trackId: String,
    val url: String,
    val popularity: Int,
    val duration: Int
)

@Serializable
data class PlaylistResult(
    val playlistId: String,
    val playlistName: String,
    val playlistUrl: String,
    val tracks: List<Track>
)
