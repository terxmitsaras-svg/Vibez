package com.nocap.terminal.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import java.io.File

class HostRepository(dir: File, scope: CoroutineScope) {
    private val store = JsonListStore(File(dir, "hosts.json"), Host.serializer(), scope)

    val hosts: StateFlow<List<Host>> = store.items

    fun get(id: String?): Host? = id?.let { wanted -> hosts.value.firstOrNull { it.id == wanted } }

    fun save(host: Host) = store.update { list ->
        if (list.any { it.id == host.id }) list.map { if (it.id == host.id) host else it } else list + host
    }

    fun delete(id: String) = store.update { list ->
        list.filter { it.id != id }.map { if (it.jumpHostId == id) it.copy(jumpHostId = null) else it }
    }

    fun clearKeyReferences(keyId: String) = store.update { list ->
        list.map { if (it.keyId == keyId) it.copy(keyId = null) else it }
    }

    fun clearIdentityReferences(identityId: String) = store.update { list ->
        list.map { if (it.identityId == identityId) it.copy(identityId = null) else it }
    }

    /** Insert or replace many hosts at once, e.g. from an import. */
    fun saveAll(hosts: List<Host>) = store.update { list ->
        val incoming = hosts.associateBy { it.id }
        list.map { incoming[it.id] ?: it } + hosts.filter { host -> list.none { it.id == host.id } }
    }

    fun markConnected(id: String) = store.update { list ->
        list.map { if (it.id == id) it.copy(lastConnectedAt = System.currentTimeMillis()) else it }
    }
}

class KeyRepository(dir: File, scope: CoroutineScope) {
    private val store = JsonListStore(File(dir, "keys.json"), SshKey.serializer(), scope)

    val keys: StateFlow<List<SshKey>> = store.items

    fun get(id: String?): SshKey? = id?.let { wanted -> keys.value.firstOrNull { it.id == wanted } }

    fun save(key: SshKey) = store.update { list ->
        if (list.any { it.id == key.id }) list.map { if (it.id == key.id) key else it } else list + key
    }

    fun saveAll(items: List<SshKey>) = store.update { list ->
        val incoming = items.associateBy { it.id }
        list.map { incoming[it.id] ?: it } + items.filter { item -> list.none { it.id == item.id } }
    }

    fun delete(id: String) = store.update { list -> list.filter { it.id != id } }
}

class IdentityRepository(dir: File, scope: CoroutineScope) {
    private val store = JsonListStore(File(dir, "identities.json"), Identity.serializer(), scope)

    val identities: StateFlow<List<Identity>> = store.items

    fun get(id: String?): Identity? = id?.let { wanted -> identities.value.firstOrNull { it.id == wanted } }

    fun save(identity: Identity) = store.update { list ->
        if (list.any { it.id == identity.id }) list.map { if (it.id == identity.id) identity else it } else list + identity
    }

    fun saveAll(items: List<Identity>) = store.update { list ->
        val incoming = items.associateBy { it.id }
        list.map { incoming[it.id] ?: it } + items.filter { item -> list.none { it.id == item.id } }
    }

    fun delete(id: String) = store.update { list -> list.filter { it.id != id } }

    fun clearKeyReferences(keyId: String) = store.update { list ->
        list.map { if (it.keyId == keyId) it.copy(keyId = null) else it }
    }
}

class SnippetRepository(dir: File, scope: CoroutineScope) {
    private val store = JsonListStore(File(dir, "snippets.json"), Snippet.serializer(), scope)

    val snippets: StateFlow<List<Snippet>> = store.items

    fun save(snippet: Snippet) = store.update { list ->
        if (list.any { it.id == snippet.id }) list.map { if (it.id == snippet.id) snippet else it } else list + snippet
    }

    fun saveAll(items: List<Snippet>) = store.update { list ->
        val incoming = items.associateBy { it.id }
        list.map { incoming[it.id] ?: it } + items.filter { item -> list.none { it.id == item.id } }
    }

    fun delete(id: String) = store.update { list -> list.filter { it.id != id } }
}

class KnownHostRepository(dir: File, scope: CoroutineScope) {
    private val store = JsonListStore(File(dir, "known_hosts.json"), KnownHost.serializer(), scope)

    val knownHosts: StateFlow<List<KnownHost>> = store.items

    fun find(host: String): List<KnownHost> = knownHosts.value.filter { it.host == host }

    /** Trust [entry], replacing any other key of the same type for the same host. */
    fun trust(entry: KnownHost) = store.update { list ->
        list.filterNot { it.host == entry.host && it.keyType == entry.keyType } + entry
    }

    fun remove(host: String, keyType: String? = null) = store.update { list ->
        list.filterNot { it.host == host && (keyType == null || it.keyType == keyType) }
    }
}
