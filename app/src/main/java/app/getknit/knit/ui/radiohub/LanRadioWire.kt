package app.getknit.knit.ui.radiohub

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import java.util.Locale

/**
 * NÓS LAN Radio v1. Independent from queued Knit messages and the Knit Mesh protocol.
 * IP multicast over a LOCAL Wi-Fi network only: no internet or cellular connection is needed.
 * Every frame is AES-256-GCM authenticated with a channel passphrase; no cleartext audio or peer ID.
 * Password possession is channel authorization; this is NOT Knit group member authentication.
 */
internal object LanRadioWire {
    const val PORT = 44786
    const val MULTICAST_ADDRESS = "239.78.79.83"
    const val PCM_BYTES = 1280 // 40 ms at 16 kHz mono signed 16-bit (256 kb/s)
    const val MAX_DATAGRAM = 1400
    private const val MAGIC = 0x4E4F534C // NOSL
    private const val VERSION = 1
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128
    private val rng = SecureRandom()

    enum class Kind(val code: Int) { HELLO(1), CLAIM(2), AUDIO(3), RELEASE(4) }

    data class Frame(
        val kind: Kind,
        val sender: Long,
        val turn: Long,
        val sequence: Int,
        val audio: ByteArray = byteArrayOf(),
    )

    fun normalizedChannel(input: String): String = input.trim().lowercase(Locale.ROOT)

    fun validCredentials(channel: String, password: String): Boolean =
        channel.length in 3..32 && channel.none { it.isISOControl() } && password.length in 10..128

    fun deriveKey(channel: String, password: String): SecretKeySpec {
        val normalized = normalizedChannel(channel)
        require(validCredentials(normalized, password))
        val salt = MessageDigest.getInstance("SHA-256")
            .digest("NOS-LAN-PTT-v1:$normalized".toByteArray(Charsets.UTF_8))
        val spec = PBEKeySpec(password.toCharArray(), salt, 120_000, 256)
        try {
            val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(key, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    fun encode(key: SecretKeySpec, frame: Frame): ByteArray? {
        if (frame.sequence <= 0 || (frame.kind == Kind.AUDIO && frame.audio.size != PCM_BYTES) ||
            (frame.kind != Kind.AUDIO && frame.audio.isNotEmpty())) return null
        val plain = ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).use {
                it.writeByte(frame.kind.code)
                it.writeLong(frame.sender)
                it.writeLong(frame.turn)
                it.writeInt(frame.sequence)
                it.writeShort(frame.audio.size)
                it.write(frame.audio)
            }
        }.toByteArray()
        val nonce = ByteArray(NONCE_BYTES).also(rng::nextBytes)
        val header = ByteArrayOutputStream().also {
            DataOutputStream(it).use { data -> data.writeInt(MAGIC); data.writeByte(VERSION) }
        }.toByteArray()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(header)
        return (header + nonce + cipher.doFinal(plain)).takeIf { it.size <= MAX_DATAGRAM }
    }

    fun decode(key: SecretKeySpec, bytes: ByteArray): Frame? {
        if (bytes.size !in (5 + NONCE_BYTES + 16 + 23)..MAX_DATAGRAM) return null
        return try {
            val header = bytes.copyOfRange(0, 5)
            DataInputStream(ByteArrayInputStream(header)).use {
                if (it.readInt() != MAGIC || it.readUnsignedByte() != VERSION) return null
            }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, bytes.copyOfRange(5, 17)))
            cipher.updateAAD(header)
            val plain = cipher.doFinal(bytes, 17, bytes.size - 17)
            DataInputStream(ByteArrayInputStream(plain)).use { input ->
                val kindCode = input.readUnsignedByte()
                val kind = Kind.entries.firstOrNull { it.code == kindCode } ?: return null
                val sender = input.readLong()
                val turn = input.readLong()
                val sequence = input.readInt()
                val length = input.readUnsignedShort()
                if (sequence <= 0 || length > PCM_BYTES || (kind == Kind.AUDIO && length != PCM_BYTES) ||
                    (kind != Kind.AUDIO && length != 0) || input.available() != length) return null
                val audio = ByteArray(length).also { input.readFully(it) }
                Frame(kind, sender, turn, sequence, audio)
            }
        } catch (_: Exception) {
            // Wrong password, damaged packet, malformed length, and tampering all fail closed.
            null
        }
    }
}

/** Drop replays and out-of-order datagrams within a per-installation random peer session. */
internal class LanRadioReplay {
    private val lastSequence = LinkedHashMap<Long, Int>()
    fun accept(frame: LanRadioWire.Frame): Boolean {
        val previous = lastSequence[frame.sender] ?: 0
        if (frame.sequence <= previous) return false
        if (lastSequence.size >= 128 && frame.sender !in lastSequence) lastSequence.remove(lastSequence.keys.first())
        lastSequence[frame.sender] = frame.sequence
        return true
    }
}

/** Local approximation only. Simultaneous transmissions are resolved by random sender ID priority. */
internal class LanRadioFloor(private val now: () -> Long = System::currentTimeMillis) {
    data class Talker(val sender: Long, val turn: Long, val expires: Long)
    private var current: Talker? = null
    fun active(): Talker? {
        if (current != null && now() > current!!.expires) current = null
        return current
    }
    fun claim(sender: Long, turn: Long): Boolean {
        val previous = active()
        if (previous != null && previous.sender != sender) {
            if (java.lang.Long.compareUnsigned(sender, previous.sender) >= 0) return false
        }
        current = Talker(sender, turn, now() + 1500L)
        return true
    }
    fun extend(sender: Long, turn: Long): Boolean {
        if (active()?.let { it.sender == sender && it.turn == turn } != true) return false
        current = Talker(sender, turn, now() + 1500L)
        return true
    }
    fun release(sender: Long, turn: Long) {
        if (active()?.let { it.sender == sender && it.turn == turn } == true) current = null
    }
    fun clear() { current = null }
}
