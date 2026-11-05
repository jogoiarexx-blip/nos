package app.getknit.knit.ui.radiohub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupPttProtocolTest {
    private val base = 1_780_000_000_000L
    private val claim = GroupPttProtocol.Signal("g-test", "alice", "turn-1", GroupPttProtocol.Kind.CLAIM, base, 12_000)

    @Test fun `v1 claim codec round trip`() {
        assertEquals(claim, GroupPttProtocol.decode(GroupPttProtocol.encode(claim)!!))
    }

    @Test fun `truncated and oversized frames are rejected`() {
        val original = GroupPttProtocol.encode(claim)!!
        assertNull(GroupPttProtocol.decode(original.copyOfRange(0, original.size - 1)))
        assertNull(GroupPttProtocol.decode(ByteArray(513)))
    }

    @Test fun `unknown version and excessive lease are refused`() {
        val bytes = GroupPttProtocol.encode(claim)!!
        bytes[4] = 10
        assertNull(GroupPttProtocol.decode(bytes))
        assertNull(GroupPttProtocol.encode(claim.copy(leaseMs = 30_000)))
    }

    @Test fun `only one local holder owns a microphone lease`() {
        var now = base
        val floor = GroupPttFloor { now }
        assertTrue(floor.claim("alice", "a", 1000))
        assertFalse(floor.claim("bob", "b", 1000))
        assertFalse(floor.release("bob", "b"))
        now += 1001
        assertTrue(floor.claim("bob", "b", 1000))
        assertTrue(floor.release("bob", "b"))
    }

    @Test fun `authenticated sender, group membership and replay are enforced`() {
        var now = base
        val floor = GroupPttFloor { now }
        assertFalse(floor.acceptRemote(claim, "mallory", setOf("alice", "bob")))
        assertFalse(floor.acceptRemote(claim, "alice", setOf("bob")))
        assertTrue(floor.acceptRemote(claim, "alice", setOf("alice", "bob")))
        assertFalse(floor.acceptRemote(claim, "alice", setOf("alice", "bob")))
        assertTrue(floor.acceptRemote(claim.copy(kind = GroupPttProtocol.Kind.RELEASE), "alice", setOf("alice", "bob")))
    }

    @Test fun `stale and future signals are rejected`() {
        val floor = GroupPttFloor { base }
        assertFalse(floor.acceptRemote(claim.copy(createdAtMs = base - 20_000), "alice", setOf("alice")))
        assertFalse(floor.acceptRemote(claim.copy(createdAtMs = base + 10_000), "alice", setOf("alice")))
    }
}
