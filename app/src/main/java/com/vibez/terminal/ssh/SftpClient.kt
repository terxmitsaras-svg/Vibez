package com.vibez.terminal.ssh

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.SftpProgressMonitor
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean

data class RemoteFile(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val isSymlink: Boolean,
    val size: Long,
    /** Milliseconds since the epoch. */
    val modifiedAt: Long,
    /** "drwxr-xr-x" style permissions. */
    val permissions: String,
)

/** Thread-safe wrapper around an SFTP channel. Blocking; use from a background thread. */
class SftpClient(
    private val channel: ChannelSftp,
    /** A connection opened just for this client, closed together with it. */
    private val ownedConnection: SshConnection?,
) : Closeable {

    private val lock = Any()

    fun home(): String = synchronized(lock) { channel.home }

    fun canonicalize(path: String): String = synchronized(lock) { channel.realpath(path) }

    /** Directory contents, directories first, each group sorted by name. */
    fun list(path: String): List<RemoteFile> = synchronized(lock) {
        channel.ls(path)
            .filter { it.filename != "." && it.filename != ".." }
            .map { entry ->
                val attrs = entry.attrs
                val fullPath = join(path, entry.filename)
                val isDirectory = attrs.isDir || (attrs.isLink && runCatching { channel.stat(fullPath).isDir }.getOrDefault(false))
                RemoteFile(
                    name = entry.filename,
                    path = fullPath,
                    isDirectory = isDirectory,
                    isSymlink = attrs.isLink,
                    size = attrs.size,
                    modifiedAt = (attrs.mTime.toLong() and 0xFFFFFFFFL) * 1000L,
                    permissions = attrs.permissionsString,
                )
            }
            .sortedWith(compareBy<RemoteFile>({ !it.isDirectory }, { it.name.lowercase() }))
    }

    fun mkdir(path: String) = synchronized(lock) { channel.mkdir(path) }

    fun rename(from: String, to: String) = synchronized(lock) { channel.rename(from, to) }

    fun chmod(path: String, mode: Int) = synchronized(lock) { channel.chmod(mode, path) }

    /** Delete a file, or a directory with everything in it. */
    fun delete(file: RemoteFile) {
        if (file.isDirectory && !file.isSymlink) {
            list(file.path).forEach { delete(it) }
            synchronized(lock) { channel.rmdir(file.path) }
        } else {
            synchronized(lock) { channel.rm(file.path) }
        }
    }

    fun download(path: String, target: OutputStream, progress: TransferProgress) = synchronized(lock) {
        channel.get(path, target, progress.monitor())
    }

    fun upload(source: InputStream, path: String, progress: TransferProgress) = synchronized(lock) {
        channel.put(source, path, progress.monitor(), ChannelSftp.OVERWRITE)
    }

    override fun close() {
        runCatching { channel.disconnect() }
        ownedConnection?.disconnect()
    }

    companion object {
        fun join(directory: String, name: String): String = if (directory.endsWith("/")) directory + name else "$directory/$name"

        fun parent(path: String): String {
            val trimmed = path.trimEnd('/')
            val index = trimmed.lastIndexOf('/')
            return if (index <= 0) "/" else trimmed.substring(0, index)
        }
    }
}

/** Reports transfer progress and allows cancelling a transfer from another thread. */
class TransferProgress(private val onProgress: (transferred: Long, total: Long) -> Unit) {
    private val cancelled = AtomicBoolean(false)

    fun cancel() = cancelled.set(true)

    internal fun monitor(): SftpProgressMonitor = object : SftpProgressMonitor {
        private var total = -1L
        private var transferred = 0L

        override fun init(op: Int, src: String?, dest: String?, max: Long) {
            total = max
            onProgress(0, total)
        }

        override fun count(count: Long): Boolean {
            transferred += count
            onProgress(transferred, total)
            return !cancelled.get()
        }

        override fun end() = Unit
    }
}
