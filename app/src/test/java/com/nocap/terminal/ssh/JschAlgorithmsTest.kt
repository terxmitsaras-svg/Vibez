package com.nocap.terminal.ssh

import com.jcraft.jsch.JSch
import com.jcraft.jsch.KEM
import org.bouncycastle.crypto.kems.MLKEMGenerator
import org.bouncycastle.crypto.params.MLKEMParameters
import org.bouncycastle.crypto.params.MLKEMPublicKeyParameters
import org.bouncycastle.pqc.crypto.ntruprime.SNTRUPrimeKEMGenerator
import org.bouncycastle.pqc.crypto.ntruprime.SNTRUPrimeParameters
import org.bouncycastle.pqc.crypto.ntruprime.SNTRUPrimePublicKeyParameters
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

/**
 * On Android JSch uses BouncyCastle for post-quantum and Edwards-curve algorithms. A BouncyCastle
 * version that does not match what JSch was compiled against only fails at runtime, when a server
 * picks that algorithm, so check every algorithm class can be loaded and actually works.
 */
class JschAlgorithmsTest {

    private val random = SecureRandom()

    private fun newInstance(className: String): Any = Class.forName(className).getDeclaredConstructor().apply { isAccessible = true }.newInstance()

    @Test
    fun everyConfiguredAlgorithmClassLoads() {
        val names = listOf("kex", "server_host_key", "cipher.s2c", "mac.s2c", "PubkeyAcceptedAlgorithms")
            .flatMap { JSch.getConfig(it).split(',') }
            .filter { it.isNotBlank() && !it.contains("-cert-v01@") }
            .distinct()
        assertTrue(names.size > 20)
        val failures = names.mapNotNull { name ->
            val className = JSch.getConfig(name) ?: return@mapNotNull "$name: no implementation configured"
            runCatching { newInstance(className) }.exceptionOrNull()?.let { "$name ($className): $it" }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun bouncyCastleImplementationsUsedOnAndroidLoad() {
        val androidClasses = listOf(
            "com.jcraft.jsch.bc.XDH",
            "com.jcraft.jsch.bc.KeyPairGenEdDSA",
            "com.jcraft.jsch.bc.SignatureEd25519",
            "com.jcraft.jsch.bc.SignatureEd448",
            "com.jcraft.jsch.bc.MLKEM768",
            "com.jcraft.jsch.bc.MLKEM1024",
            "com.jcraft.jsch.bc.SNTRUP761",
            "com.jcraft.jsch.bc.ChaCha20Poly1305",
        )
        androidClasses.forEach { newInstance(it) }
    }

    @Test
    fun mlKem768RoundTrip() {
        val kem = newInstance("com.jcraft.jsch.bc.MLKEM768") as KEM
        kem.init()
        val serverSide = MLKEMGenerator(random).generateEncapsulated(MLKEMPublicKeyParameters(MLKEMParameters.ml_kem_768, kem.publicKey))
        assertArrayEquals(serverSide.secret, kem.decapsulate(serverSide.encapsulation))
    }

    @Test
    fun sntrup761RoundTrip() {
        val kem = newInstance("com.jcraft.jsch.bc.SNTRUP761") as KEM
        kem.init()
        val serverSide = SNTRUPrimeKEMGenerator(random)
            .generateEncapsulated(SNTRUPrimePublicKeyParameters(SNTRUPrimeParameters.sntrup761, kem.publicKey))
        assertArrayEquals(serverSide.secret, kem.decapsulate(serverSide.encapsulation))
    }
}
