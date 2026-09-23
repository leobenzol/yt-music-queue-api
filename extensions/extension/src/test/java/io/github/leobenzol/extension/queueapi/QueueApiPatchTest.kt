package io.github.leobenzol.extension.queueapi

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class QueueApiPatchTest {
    @Test
    fun `the right token is accepted`() = assertTrue(QueueApiPatch.isAuthorized("secret-token", "secret-token"))

    @Test
    fun `a wrong or missing token is rejected`() {
        assertFalse(QueueApiPatch.isAuthorized("secret-token", "secret-tokem"))
        assertFalse(QueueApiPatch.isAuthorized("secret-token", ""))
        assertFalse(QueueApiPatch.isAuthorized("secret-token", null))
    }

    @Test
    fun `without a patched token every request is rejected`() {
        assertFalse(QueueApiPatch.isAuthorized("", ""))
        assertFalse(QueueApiPatch.isAuthorized("", null))
    }
}
