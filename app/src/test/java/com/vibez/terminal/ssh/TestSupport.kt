package com.vibez.terminal.ssh

import com.vibez.terminal.data.SecretCipher
import java.io.Closeable
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList

/** Reversible stand-in for the Android Keystore backed SecretBox. */
class FakeCipher : SecretCipher {
    override fun encrypt(plain: ByteArray): String = "fake:" + Base64.getEncoder().encodeToString(plain)

    override fun decrypt(encoded: String): ByteArray = Base64.getDecoder().decode(encoded.removePrefix("fake:"))
}

/** Answers prompts from a background thread, the way the UI would, and records what was asked. */
class PromptResponder(
    private val broker: PromptBroker,
    private val answer: (PromptRequest<*>) -> Any?,
) : Closeable {
    val asked = CopyOnWriteArrayList<PromptRequest<*>>()

    @Volatile
    private var running = true

    private val thread = Thread {
        val handled = HashSet<PromptRequest<*>>()
        while (running) {
            for (request in broker.requests.value) {
                if (handled.add(request)) {
                    asked += request
                    respond(request, answer(request))
                }
            }
            Thread.sleep(5)
        }
    }.apply {
        isDaemon = true
        start()
    }

    @Suppress("UNCHECKED_CAST")
    private fun respond(request: PromptRequest<*>, value: Any?) {
        when (request) {
            is PromptRequest.Confirm -> request.respond(value as Boolean?)
            is PromptRequest.Secret -> request.respond(value as SecretAnswer?)
            is PromptRequest.KeyboardInteractive -> request.respond(value as List<String>?)
        }
    }

    override fun close() {
        running = false
        thread.join(1000)
    }
}
