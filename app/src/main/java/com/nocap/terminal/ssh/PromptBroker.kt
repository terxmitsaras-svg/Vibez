package com.nocap.terminal.ssh

import android.os.Looper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import java.util.Collections
import java.util.WeakHashMap

data class SecretAnswer(val value: String, val remember: Boolean)

data class PromptField(val label: String, val echo: Boolean)

/** A question for the user, answered through [respond]. A null response means the user cancelled. */
sealed class PromptRequest<T : Any>(val owner: Any) {
    internal val result = CompletableDeferred<T?>()

    fun respond(value: T?) {
        result.complete(value)
    }

    class Secret(
        owner: Any,
        val title: String,
        val message: String,
        val label: String,
        val offerRemember: Boolean,
    ) : PromptRequest<SecretAnswer>(owner)

    class Confirm(
        owner: Any,
        val title: String,
        val message: String,
        val confirmLabel: String,
        val dismissLabel: String,
        val isWarning: Boolean,
    ) : PromptRequest<Boolean>(owner)

    class KeyboardInteractive(
        owner: Any,
        val title: String,
        val instruction: String,
        val fields: List<PromptField>,
    ) : PromptRequest<List<String>>(owner)
}

/**
 * Lets background threads (SSH connections) ask the user questions. Requests are queued and shown
 * one at a time by the UI, which observes [requests]. The asking thread blocks until answered.
 */
class PromptBroker {

    private val queue = MutableStateFlow<List<PromptRequest<*>>>(emptyList())
    private val cancelledOwners: MutableSet<Any> = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap()))

    val requests: StateFlow<List<PromptRequest<*>>> = queue.asStateFlow()

    fun askSecret(owner: Any, title: String, message: String, label: String, offerRemember: Boolean): SecretAnswer? =
        await(PromptRequest.Secret(owner, title, message, label, offerRemember))

    fun confirm(
        owner: Any,
        title: String,
        message: String,
        confirmLabel: String,
        dismissLabel: String,
        isWarning: Boolean = false,
    ): Boolean = await(PromptRequest.Confirm(owner, title, message, confirmLabel, dismissLabel, isWarning)) ?: false

    fun keyboardInteractive(owner: Any, title: String, instruction: String, fields: List<PromptField>): List<String>? =
        await(PromptRequest.KeyboardInteractive(owner, title, instruction, fields))

    /** Cancel all current and future questions asked on behalf of [owner], e.g. when its session is closed. */
    fun cancelAll(owner: Any) {
        cancelledOwners.add(owner)
        queue.value.filter { it.owner === owner }.forEach { it.respond(null) }
    }

    private fun <T : Any> await(request: PromptRequest<T>): T? {
        check(Looper.getMainLooper()?.isCurrentThread != true) { "Prompts must not be awaited on the main thread" }
        queue.update { it + request }
        // Checked after enqueueing so that a concurrent cancelAll() cannot be missed.
        if (request.owner in cancelledOwners) request.respond(null)
        return try {
            runBlocking { request.result.await() }
        } finally {
            queue.update { list -> list.filter { it !== request } }
        }
    }
}
