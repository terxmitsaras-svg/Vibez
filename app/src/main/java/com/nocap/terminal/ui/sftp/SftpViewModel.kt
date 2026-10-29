package com.nocap.terminal.ui.sftp

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jcraft.jsch.ChannelSftp
import com.nocap.terminal.AppContainer
import com.nocap.terminal.data.Host
import com.nocap.terminal.ssh.RemoteFile
import com.nocap.terminal.ssh.SftpClient
import com.nocap.terminal.ssh.TransferProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlin.concurrent.thread

data class Transfer(val name: String, val isUpload: Boolean, val transferred: Long, val total: Long)

enum class SftpSort { NAME, SIZE, DATE }

/** A remote text file open in the editor. */
data class EditedFile(val file: RemoteFile, val text: String)

data class SftpUiState(
    val hostName: String = "",
    val connecting: Boolean = true,
    val error: String? = null,
    val path: String = "",
    val files: List<RemoteFile> = emptyList(),
    val loading: Boolean = false,
    val transfer: Transfer? = null,
    val showHidden: Boolean = false,
    val sort: SftpSort = SftpSort.NAME,
    val filter: String = "",
    val editing: EditedFile? = null,
) {
    /** [files] as shown: hidden files, filter and sort order applied, folders first. */
    val visibleFiles: List<RemoteFile>
        get() {
            val needle = filter.trim().lowercase()
            val shown = files.filter { (showHidden || !it.name.startsWith(".")) && (needle.isEmpty() || it.name.lowercase().contains(needle)) }
            val order = when (sort) {
                SftpSort.NAME -> compareBy<RemoteFile> { it.name.lowercase() }
                SftpSort.SIZE -> compareByDescending { it.size }
                SftpSort.DATE -> compareByDescending { it.modifiedAt }
            }
            return shown.sortedWith(compareBy<RemoteFile> { !it.isDirectory }.then(order))
        }
}

class SftpViewModel(private val container: AppContainer, private val hostId: String) : ViewModel() {

    private val uiState = MutableStateFlow(SftpUiState())
    val state: StateFlow<SftpUiState> = uiState.asStateFlow()

    private val messageEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = messageEvents.asSharedFlow()

    /** Identifies this browser's questions to the user, so they can be cancelled. */
    private val promptOwner = Any()

    @Volatile
    private var client: SftpClient? = null
    private var activeTransfer: TransferProgress? = null

    init {
        connect()
    }

    private fun findHost(): Host? =
        container.hosts.get(hostId) ?: container.sessions.tabs.value.firstNotNullOfOrNull { tab -> tab.host?.takeIf { it.id == hostId } }

    fun connect() {
        viewModelScope.launch {
            uiState.update { it.copy(connecting = true, error = null, hostName = findHost()?.displayName.orEmpty()) }
            try {
                val opened = withContext(Dispatchers.IO) { openClient() }
                client = opened
                val home = withContext(Dispatchers.IO) { runCatching { opened.canonicalize(".") }.getOrDefault("/") }
                uiState.update { it.copy(connecting = false) }
                open(home)
            } catch (e: Exception) {
                uiState.update { it.copy(connecting = false, error = e.message ?: "Connection failed") }
            }
        }
    }

    private fun openClient(): SftpClient {
        val host = findHost() ?: throw IOException("This host no longer exists")
        // Reuse the connection of an open terminal tab to the same host: no second login needed.
        container.sessions.tabs.value
            .firstNotNullOfOrNull { tab -> tab.sshBackend?.takeIf { it.host.id == hostId && it.isConnected } }
            ?.let { backend -> runCatching { return SftpClient(backend.openSftp(), ownedConnection = null) } }

        val connection = container.connector.connect(host, owner = promptOwner, status = {})
        try {
            val channel = connection.session.openChannel("sftp") as ChannelSftp
            channel.connect(CHANNEL_TIMEOUT_MS)
            return SftpClient(channel, connection)
        } catch (e: Exception) {
            connection.disconnect()
            throw IOException("SFTP is not available on this server: ${e.message}", e)
        }
    }

    fun open(path: String) = runOperation(showLoading = true) { client ->
        val files = client.list(path)
        uiState.update { it.copy(path = path, files = files) }
    }

    fun refresh() = open(uiState.value.path)

    fun goUp(): Boolean {
        val path = uiState.value.path
        if (path.isEmpty() || path == "/") return false
        open(SftpClient.parent(path))
        return true
    }

    fun createDirectory(name: String) = runOperation {
        it.mkdir(SftpClient.join(uiState.value.path, name))
        reload(it)
    }

    fun rename(file: RemoteFile, newName: String) = runOperation {
        it.rename(file.path, SftpClient.join(SftpClient.parent(file.path), newName))
        reload(it)
    }

    fun delete(file: RemoteFile) = runOperation(showLoading = true) {
        it.delete(file)
        reload(it)
    }

    fun changePermissions(file: RemoteFile, octal: String) = runOperation {
        it.chmod(file.path, octal.toInt(8))
        reload(it)
    }

    fun download(file: RemoteFile, target: Uri, context: Context) = runTransfer(file.name, isUpload = false, file.size) { client, progress ->
        val output = context.contentResolver.openOutputStream(target) ?: throw IOException("Cannot write to the selected file")
        output.use { client.download(file.path, it, progress) }
        messageEvents.tryEmit("Downloaded ${file.name}")
    }

    fun upload(source: Uri, context: Context) {
        val (name, size) = describe(context, source)
        runTransfer(name, isUpload = true, size) { client, progress ->
            val input = context.contentResolver.openInputStream(source) ?: throw IOException("Cannot read the selected file")
            input.use { client.upload(it, SftpClient.join(uiState.value.path, name), progress) }
            reload(client)
            messageEvents.tryEmit("Uploaded $name")
        }
    }

    fun setShowHidden(show: Boolean) = uiState.update { it.copy(showHidden = show) }

    fun setSort(sort: SftpSort) = uiState.update { it.copy(sort = sort) }

    fun setFilter(filter: String) = uiState.update { it.copy(filter = filter) }

    /** Load a small text file into the editor. */
    fun edit(file: RemoteFile) {
        if (file.size > MAX_EDIT_BYTES) {
            messageEvents.tryEmit("${file.name} is too large to edit here (over 1 MB)")
            return
        }
        runOperation(showLoading = true) { client ->
            val bytes = ByteArrayOutputStream().also { client.download(file.path, it, TransferProgress { _, _ -> }) }.toByteArray()
            if (bytes.any { it.toInt() == 0 }) {
                messageEvents.tryEmit("${file.name} looks like a binary file")
            } else {
                uiState.update { it.copy(editing = EditedFile(file, bytes.decodeToString())) }
            }
        }
    }

    fun saveEdit(text: String) {
        val edited = uiState.value.editing ?: return
        runOperation(showLoading = true) { client ->
            client.upload(text.encodeToByteArray().inputStream(), edited.file.path, TransferProgress { _, _ -> })
            uiState.update { it.copy(editing = null) }
            reload(client)
            messageEvents.tryEmit("Saved ${edited.file.name}")
        }
    }

    fun closeEditor() = uiState.update { it.copy(editing = null) }

    /** Download to the app cache and hand the file to [onReady] for opening in another app. */
    fun downloadForOpening(file: RemoteFile, context: Context, onReady: (File) -> Unit) {
        val directory = File(context.cacheDir, "sftp").apply { mkdirs() }
        val target = File(directory, file.name)
        runTransfer(file.name, isUpload = false, file.size) { client, progress ->
            target.outputStream().use { client.download(file.path, it, progress) }
            viewModelScope.launch { onReady(target) }
        }
    }

    fun cancelTransfer() {
        activeTransfer?.cancel()
    }

    private fun reload(client: SftpClient) {
        val path = uiState.value.path
        val files = client.list(path)
        uiState.update { it.copy(files = files) }
    }

    private fun runOperation(showLoading: Boolean = false, block: (SftpClient) -> Unit) {
        val client = client ?: return
        viewModelScope.launch {
            if (showLoading) uiState.update { it.copy(loading = true) }
            try {
                withContext(Dispatchers.IO) { block(client) }
            } catch (e: Exception) {
                messageEvents.tryEmit(e.message ?: "Operation failed")
            } finally {
                if (showLoading) uiState.update { it.copy(loading = false) }
            }
        }
    }

    private fun runTransfer(name: String, isUpload: Boolean, size: Long, block: (SftpClient, TransferProgress) -> Unit) {
        val client = client ?: return
        if (uiState.value.transfer != null) {
            messageEvents.tryEmit("Wait for the current transfer to finish")
            return
        }
        val progress = TransferProgress { transferred, total ->
            val knownTotal = if (total > 0) total else size
            uiState.update { it.copy(transfer = Transfer(name, isUpload, transferred, knownTotal)) }
        }
        activeTransfer = progress
        uiState.update { it.copy(transfer = Transfer(name, isUpload, 0, size)) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { block(client, progress) }
            } catch (e: Exception) {
                messageEvents.tryEmit("Transfer of $name failed: ${e.message}")
            } finally {
                activeTransfer = null
                uiState.update { it.copy(transfer = null) }
            }
        }
    }

    private fun describe(context: Context, uri: Uri): Pair<String, Long> {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val name = cursor.getString(0) ?: "upload"
                val size = if (cursor.isNull(1)) -1L else cursor.getLong(1)
                return name to size
            }
        }
        return (uri.lastPathSegment ?: "upload") to -1L
    }

    override fun onCleared() {
        container.prompts.cancelAll(promptOwner)
        activeTransfer?.cancel()
        val client = client ?: return
        // Closing sends packets to the server; keep that off the main thread.
        thread(name = "sftp-close") { client.close() }
    }

    private companion object {
        const val CHANNEL_TIMEOUT_MS = 15_000
        const val MAX_EDIT_BYTES = 1024 * 1024
    }
}
