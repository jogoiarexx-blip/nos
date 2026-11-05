package app.getknit.knit.ui.radiohub

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException

/**
 * NÓS group PTT v1: a bounded, transport-independent wire schema for short-lived floor signals.
 *
 * SECURITY: these bytes are *not* authenticated by this codec. The eventual mesh adapter MUST authenticate
 * the sender with Knit's group ratchet, check membership against the effective local roster, and never use
 * claimed senderId as proof of identity. Until that adapter exists the app only uses a LOCAL floor gate and
 * sends completed voice bursts through Knit's existing encrypted group-attachment path.
 *
 * No code in REV5 transmits these packets. Do not advertise live group PTT as working.
 */
internal object GroupPttProtocol {
    private const val MAGIC = 0x4E4F5350 // "NOSP"
    private const val VERSION = 1
    private const val MAX_GROUP_BYTES = 128
    private const val MAX_PEER_BYTES = 128
    private const val MAX_TURN_BYTES = 64
    private const val MAX_WIRE_BYTES = 512
    const val MAX_LEASE_MS = 15_000L
    const val MAX_CLOCK_SKEW_MS = 5_000L

    enum class Kind(val wire: Int) { CLAIM(1), RELEASE(2), HEARTBEAT(3) }

    data class Signal(
        val groupId: String,
        val senderId: String,
        val turnId: String,
        val kind: Kind,
        val createdAtMs: Long,
        val leaseMs: Long,
    )

    fun encode(signal: Signal): ByteArray? {
        if (!valid(signal)) return null
        val stream = ByteArrayOutputStream()
        DataOutputStream(stream).use { out ->
            out.writeInt(MAGIC)
            out.writeByte(VERSION)
            out.writeByte(signal.kind.wire)
            putString(out, signal.groupId)
            putString(out, signal.senderId)
            putString(out, signal.turnId)
            out.writeLong(signal.createdAtMs)
            out.writeShort(signal.leaseMs.toInt())
        }
        return stream.toByteArray().takeIf { it.size <= MAX_WIRE_BYTES }
    }

    fun decode(wire: ByteArray): Signal? {
        if (wire.size !in 1..MAX_WIRE_BYTES) return null
        return try {
            DataInputStream(ByteArrayInputStream(wire)).use { input ->
                if (input.readInt() != MAGIC || input.readUnsignedByte() != VERSION) return null
                val kind = Kind.entries.firstOrNull { it.wire == input.readUnsignedByte() } ?: return null
                val groupId = readString(input, MAX_GROUP_BYTES) ?: return null
                val senderId = readString(input, MAX_PEER_BYTES) ?: return null
                val turnId = readString(input, MAX_TURN_BYTES) ?: return null
                val createdAtMs = input.readLong()
                val leaseMs = input.readUnsignedShort().toLong()
                val decoded = Signal(groupId, senderId, turnId, kind, createdAtMs, leaseMs)
                decoded.takeIf { input.available() == 0 && valid(it) }
            }
        } catch (_: EOFException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /** Sender and membership are validated by the authenticated transport, not by [decode]. */
    private fun valid(s: Signal): Boolean =
        stringFits(s.groupId, MAX_GROUP_BYTES) &&
            stringFits(s.senderId, MAX_PEER_BYTES) &&
            stringFits(s.turnId, MAX_TURN_BYTES) &&
            s.createdAtMs > 0 && s.leaseMs in 1..MAX_LEASE_MS

    private fun stringFits(value: String, max: Int): Boolean {
        val bytes = value.encodeToByteArray()
        return bytes.isNotEmpty() && bytes.size <= max && value.none { it.isISOControl() }
    }

    private fun putString(out: DataOutputStream, value: String) {
        val bytes = value.encodeToByteArray()
        out.writeByte(bytes.size)
        out.write(bytes)
    }

    private fun readString(input: DataInputStream, max: Int): String? {
        val len = input.readUnsignedByte()
        if (len !in 1..max || len > input.available()) return null
        val bytes = ByteArray(len)
        input.readFully(bytes)
        val decoded = bytes.toString(Charsets.UTF_8)
        return decoded.takeIf { it.encodeToByteArray().contentEquals(bytes) && stringFits(it, max) }
    }
}

/**
 * Bounded single-holder floor arbitration. This object is used locally for microphone ownership in REV5.
 * Future authenticated group messages may feed [acceptRemote]; its caller MUST verify sender identity,
 * roster, replay and clock, and must treat network partitions as conflicting floors rather than global locks.
 *
 * Never synchronise on sender-supplied wall time to revoke another participant's legitimate capture.
 */
internal class GroupPttFloor(private val clock: () -> Long = System::currentTimeMillis) {
    data class Holder(val nodeId: String, val turnId: String, val expiresAt: Long)

    private var holder: Holder? = null
    private val seen = LinkedHashMap<String, Long>()

    fun current(): Holder? {
        val live = holder
        if (live != null && clock() >= live.expiresAt) holder = null
        return holder
    }

    fun claim(nodeId: String, turnId: String, leaseMs: Long = GroupPttProtocol.MAX_LEASE_MS): Boolean {
        if (nodeId.isBlank() || turnId.isBlank() || leaseMs !in 1..GroupPttProtocol.MAX_LEASE_MS) return false
        val previous = current()
        if (previous != null && (previous.nodeId != nodeId || previous.turnId != turnId)) return false
        holder = Holder(nodeId, turnId, clock() + leaseMs)
        return true
    }

    fun release(nodeId: String, turnId: String): Boolean {
        val previous = current() ?: return false
        if (previous.nodeId != nodeId || previous.turnId != turnId) return false
        holder = null
        return true
    }

    /** Only call this on *authenticated* signals from existing, unblocked group members. */
    fun acceptRemote(signal: GroupPttProtocol.Signal, authenticatedSender: String, members: Set<String>): Boolean {
        val now = clock()
        if (authenticatedSender != signal.senderId || authenticatedSender !in members) return false
        if (signal.createdAtMs > now + GroupPttProtocol.MAX_CLOCK_SKEW_MS ||
            now - signal.createdAtMs > signal.leaseMs + GroupPttProtocol.MAX_CLOCK_SKEW_MS
        ) return false
        val replay = "${signal.senderId}:${signal.turnId}:${signal.kind.wire}:${signal.createdAtMs}"
        if (seen.containsKey(replay)) return false
        while (seen.size >= 128) seen.remove(seen.keys.first())
        seen[replay] = now
        return when (signal.kind) {
            GroupPttProtocol.Kind.CLAIM -> claim(signal.senderId, signal.turnId, signal.leaseMs)
            GroupPttProtocol.Kind.HEARTBEAT ->
                if (current()?.let { it.nodeId == signal.senderId && it.turnId == signal.turnId } == true) {
                    claim(signal.senderId, signal.turnId, signal.leaseMs)
                } else false
            GroupPttProtocol.Kind.RELEASE -> release(signal.senderId, signal.turnId)
        }
    }
}
