package com.vibez.api

import com.vibez.model.GeneratePlaylistRequest
import com.vibez.model.PlaylistResult
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class VibezApi(
    private val baseUrl: String = "http://10.0.2.2:4000"
) {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    suspend fun generatePlaylist(request: GeneratePlaylistRequest): PlaylistResult {
        return client.post("$baseUrl/playlists/generate") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }
}
