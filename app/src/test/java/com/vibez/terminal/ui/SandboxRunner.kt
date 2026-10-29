package com.vibez.terminal.ui

import org.junit.runners.model.FrameworkMethod
import org.robolectric.RobolectricTestRunner
import org.robolectric.internal.bytecode.InstrumentationConfiguration

/**
 * Robolectric runner that leaves the SSH test server (Apache MINA SSHD, on the same test classpath)
 * to the regular class loader. Otherwise its java.nio file system provider is loaded twice and
 * breaks Robolectric's own jar access.
 */
class SandboxRunner(testClass: Class<*>) : RobolectricTestRunner(testClass) {
    override fun createClassLoaderConfig(method: FrameworkMethod): InstrumentationConfiguration =
        InstrumentationConfiguration.Builder(super.createClassLoaderConfig(method))
            .doNotAcquirePackage("org.apache.sshd")
            .build()
}
