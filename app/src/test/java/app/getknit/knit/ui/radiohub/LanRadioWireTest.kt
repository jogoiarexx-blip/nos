package app.getknit.knit.ui.radiohub

import org.junit.Assert.*
import org.junit.Test

class LanRadioWireTest {
    private val key = LanRadioWire.deriveKey("geral", "canal-secreto-123")

    @Test fun `audio is authenticated and bounded`() {
        val pcm = ByteArray(LanRadioWire.PCM_BYTES) { it.toByte() }
        val original = LanRadioWire.Frame(LanRadioWire.Kind.AUDIO, 23L, 59L, 1, pcm)
        val encoded = LanRadioWire.encode(key, original)!!
        assertTrue(encoded.size <= LanRadioWire.MAX_DATAGRAM)
        assertArrayEquals(pcm, LanRadioWire.decode(key, encoded)!!.audio)
        encoded[encoded.lastIndex] = (encoded.last().toInt() xor 1).toByte()
        assertNull(LanRadioWire.decode(key, encoded))
        assertNull(LanRadioWire.encode(key, original.copy(audio = byteArrayOf(1))))
    }

    @Test fun `other passwords cannot hear or impersonate radios`() {
        val encoded = LanRadioWire.encode(key, LanRadioWire.Frame(LanRadioWire.Kind.HELLO, 20, 0, 1))!!
        val other = LanRadioWire.deriveKey("geral", "senha-diferente-321")
        assertNull(LanRadioWire.decode(other, encoded))
    }

    @Test fun `remote packets are replay protected`() {
        val replay = LanRadioReplay()
        val frame = LanRadioWire.Frame(LanRadioWire.Kind.CLAIM, 23, 1, 7)
        assertTrue(replay.accept(frame))
        assertFalse(replay.accept(frame))
        assertFalse(replay.accept(frame.copy(sequence = 6)))
        assertTrue(replay.accept(frame.copy(sequence = 8)))
    }

    @Test fun `lower radio ID wins simultaneous floor claims`() {
        var now = 1000L
        val floor = LanRadioFloor { now }
        assertTrue(floor.claim(20, 1))
        assertFalse(floor.claim(30, 2))
        assertTrue(floor.claim(10, 3))
        floor.release(20, 1)
        assertEquals(10L, floor.active()?.sender)
        now += 1501
        assertNull(floor.active())
    }
}
