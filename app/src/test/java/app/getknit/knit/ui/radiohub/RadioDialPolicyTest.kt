package app.getknit.knit.ui.radiohub

import app.getknit.knit.mesh.protocol.Protocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RadioDialPolicyTest {
    private val supportsWalkie = Protocol.CAP_WALKIE

    private fun check(
        peer: String = "alice",
        self: String = "me",
        blocked: Set<String> = emptySet(),
        caps: Long? = supportsWalkie,
        enabled: Boolean = true,
    ) = radioDialBlock(peer, self, blocked, caps, enabled)

    @Test fun `valid capable peer can start a walkie call`() = assertNull(check())

    @Test fun `blocked peers cannot be called even when profile missing`() {
        assertEquals(RadioDialBlock.BLOCKED, check(blocked = setOf("alice")))
        assertEquals(RadioDialBlock.BLOCKED, check(blocked = setOf("alice"), caps = null))
    }

    @Test fun `self and blank id rejected`() {
        assertEquals(RadioDialBlock.SELF, check(peer = "me"))
        assertEquals(RadioDialBlock.INVALID_PEER, check(peer = ""))
    }

    @Test fun `unsupported peer rejected without live fallback`() {
        assertEquals(RadioDialBlock.INVALID_PEER, check(caps = null))
        assertEquals(RadioDialBlock.NOT_SUPPORTED, check(caps = 0L))
    }

    @Test fun `build without walkie cannot dial`() {
        assertEquals(RadioDialBlock.DISABLED, check(enabled = false))
    }
}
