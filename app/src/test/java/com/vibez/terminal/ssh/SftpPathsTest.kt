package com.vibez.terminal.ssh

import org.junit.Assert.assertEquals
import org.junit.Test

class SftpPathsTest {
    @Test
    fun joinAndParent() {
        assertEquals("/a/b", SftpClient.join("/a", "b"))
        assertEquals("/b", SftpClient.join("/", "b"))
        assertEquals("/a", SftpClient.parent("/a/b"))
        assertEquals("/a", SftpClient.parent("/a/b/"))
        assertEquals("/", SftpClient.parent("/a"))
        assertEquals("/", SftpClient.parent("/"))
    }
}
