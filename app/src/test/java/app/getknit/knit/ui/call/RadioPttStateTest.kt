package app.getknit.knit.ui.call

import app.getknit.knit.call.TalkLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioPttStateTest {
    @Test fun `unknown and missing radio routes cannot record`() {
        assertEquals(RadioPttMode.CHECKING_ROUTE, radioPttState(Talk.Idle(), null).mode)
        assertFalse(radioPttState(Talk.Idle(), null).canStart)
        assertEquals(RadioPttMode.NO_ROUTE, radioPttState(Talk.Idle(), TalkLink.NONE).mode)
        assertFalse(radioPttState(Talk.Idle(), TalkLink.NONE).canStart)
    }

    @Test fun `direct mesh and relay paths permit a new burst`() {
        listOf(TalkLink.DIRECT, TalkLink.MESH, TalkLink.SPOOL).forEach { link ->
            assertEquals(RadioPttMode.READY, radioPttState(Talk.Idle(), link).mode)
            assertTrue(radioPttState(Talk.Idle(), link).canStart)
        }
    }

    @Test fun `recording stays active if route fluctuates`() {
        assertEquals(RadioPttMode.RECORDING, radioPttState(Talk.Recording(500, 0.3f), TalkLink.NONE).mode)
        assertFalse(radioPttState(Talk.Recording(500, 0.3f), TalkLink.NONE).canStart)
    }

    @Test fun `receiving and flushing cannot be interrupted by another press`() {
        assertEquals(RadioPttMode.RECEIVING, radioPttState(Talk.Hearing, TalkLink.DIRECT).mode)
        assertFalse(radioPttState(Talk.Hearing, TalkLink.DIRECT).canStart)
        assertEquals(RadioPttMode.SENDING, radioPttState(Talk.Sending, TalkLink.MESH).mode)
        assertFalse(radioPttState(Talk.Sending, TalkLink.MESH).canStart)
    }

    @Test fun `sent only means emitted onto the network`() {
        assertEquals(RadioPttMode.SENT_TO_NETWORK, radioPttState(Talk.Idle(Talk.Note.SENT), TalkLink.DIRECT).mode)
        assertTrue(radioPttState(Talk.Idle(Talk.Note.SENT), TalkLink.DIRECT).canStart)
    }

    @Test fun `microphone errors and short audio allow retry if route exists`() {
        val expected = mapOf(
            Talk.Note.NO_MIC to RadioPttMode.MICROPHONE_BUSY,
            Talk.Note.NOT_SENT to RadioPttMode.SEND_FAILED,
            Talk.Note.NO_LINK to RadioPttMode.SEND_FAILED,
            Talk.Note.TOO_SHORT to RadioPttMode.TOO_SHORT,
        )
        expected.forEach { (note, mode) ->
            assertEquals(mode, radioPttState(Talk.Idle(note), TalkLink.DIRECT).mode)
            assertTrue(radioPttState(Talk.Idle(note), TalkLink.DIRECT).canStart)
            assertFalse(radioPttState(Talk.Idle(note), TalkLink.NONE).canStart)
        }
    }
}
