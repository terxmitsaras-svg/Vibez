package com.nocap.terminal.data

import android.util.Log
import androidx.core.util.AtomicFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
private data class Envelope<T>(val version: Int, val items: List<T>)

val StoreJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = false
}

/**
 * A list of items persisted as JSON. Reads are served from memory; every update is written to disk
 * atomically in the background, conflating bursts of updates into a single write.
 */
class JsonListStore<T>(
    private val file: File,
    itemSerializer: KSerializer<T>,
    scope: CoroutineScope,
) {
    private val serializer = Envelope.serializer(itemSerializer)
    private val atomicFile = AtomicFile(file)
    private val lock = Any()
    private val state = MutableStateFlow(load())
    private val pendingWrites = Channel<Unit>(Channel.CONFLATED)

    val items: StateFlow<List<T>> = state.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            for (ignored in pendingWrites) write(state.value)
        }
    }

    fun update(transform: (List<T>) -> List<T>) {
        synchronized(lock) {
            state.value = transform(state.value)
        }
        pendingWrites.trySend(Unit)
    }

    private fun load(): List<T> {
        if (!file.exists()) return emptyList()
        return try {
            StoreJson.decodeFromString(serializer, atomicFile.readFully().decodeToString()).items
        } catch (e: Exception) {
            // Never silently drop user data: keep the unreadable file around for recovery.
            val backup = File(file.parentFile, "${file.name}.corrupt-${System.currentTimeMillis()}")
            Log.e(TAG, "Cannot read ${file.name}, moved to ${backup.name}", e)
            file.renameTo(backup)
            emptyList()
        }
    }

    private fun write(items: List<T>) {
        val bytes = StoreJson.encodeToString(serializer, Envelope(VERSION, items)).encodeToByteArray()
        val stream = try {
            atomicFile.startWrite()
        } catch (e: Exception) {
            Log.e(TAG, "Cannot write ${file.name}", e)
            return
        }
        try {
            stream.write(bytes)
            atomicFile.finishWrite(stream)
        } catch (e: Exception) {
            atomicFile.failWrite(stream)
            Log.e(TAG, "Cannot write ${file.name}", e)
        }
    }

    private companion object {
        const val TAG = "JsonListStore"
        const val VERSION = 1
    }
}
