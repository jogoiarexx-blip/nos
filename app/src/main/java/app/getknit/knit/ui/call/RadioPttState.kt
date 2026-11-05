package app.getknit.knit.ui.call

import app.getknit.knit.call.TalkLink

/**
 * The on-screen radio state is derived ONLY from Knit's real walkie state and its actual link.
 * No timer, fake peer, or optimistic delivery confirmation is introduced here.
 *
 * A known route permits a new burst even after a failed one; while the remote peer is
 * speaking or an outgoing burst is being flushed, another press must wait.
 */
internal enum class RadioPttMode {
    CHECKING_ROUTE,
    NO_ROUTE,
    READY,
    RECORDING,
    RECEIVING,
    SENDING,
    SENT_TO_NETWORK,
    TOO_SHORT,
    MICROPHONE_BUSY,
    SEND_FAILED,
}

internal data class RadioPttState(
    val mode: RadioPttMode,
    val canStart: Boolean,
)

internal fun radioPttState(talk: Talk, link: TalkLink?): RadioPttState {
    val mode =
        when (talk) {
            is Talk.Recording -> RadioPttMode.RECORDING
            Talk.Hearing -> RadioPttMode.RECEIVING
            Talk.Sending -> RadioPttMode.SENDING
            is Talk.Idle ->
                when {
                    link == null -> RadioPttMode.CHECKING_ROUTE
                    link == TalkLink.NONE -> RadioPttMode.NO_ROUTE
                    talk.note == Talk.Note.SENT -> RadioPttMode.SENT_TO_NETWORK
                    talk.note == Talk.Note.TOO_SHORT -> RadioPttMode.TOO_SHORT
                    talk.note == Talk.Note.NO_MIC -> RadioPttMode.MICROPHONE_BUSY
                    talk.note == Talk.Note.NOT_SENT -> RadioPttMode.SEND_FAILED
                    talk.note == Talk.Note.NO_LINK -> RadioPttMode.SEND_FAILED
                    else -> RadioPttMode.READY
                }
        }
    return RadioPttState(
        mode = mode,
        canStart = talk is Talk.Idle && link != null && link != TalkLink.NONE,
    )
}
