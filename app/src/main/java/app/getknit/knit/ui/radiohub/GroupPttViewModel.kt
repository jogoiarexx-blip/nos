package app.getknit.knit.ui.radiohub

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.getknit.knit.R
import app.getknit.knit.data.AttachmentStore
import app.getknit.knit.data.GroupRepository
import app.getknit.knit.data.VoiceAudio
import app.getknit.knit.data.group.GroupEntity
import app.getknit.knit.data.group.GroupMembersStore
import app.getknit.knit.data.group.toGroupInfo
import app.getknit.knit.identity.Identity
import app.getknit.knit.mesh.MeshController
import app.getknit.knit.ui.voice.VoiceRecorder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/** Press-and-send short voice bursts over Knit's EXISTING encrypted, queued group-message route.
 * Not live voice streaming. All group recipients can replay the received burst like a voice note.
 * This deliberately does not modify the 1:1 walkie transport or the ordinary voice-note composer.
 */
class GroupPttViewModel(
    private val groups: GroupRepository,
    private val attachments: AttachmentStore,
    private val mesh: MeshController,
    private val identity: Identity,
    context: Context,
) : ViewModel() {
    internal enum class Phase { LOADING, READY, RECORDING, SENDING, ERROR }

    internal data class State(
        val phase: Phase = Phase.LOADING,
        val title: String = "",
        val members: Int = 0,
        val elapsedMs: Long = 0L,
        val amplitude: Float = 0f,
        @param:StringRes val error: Int? = null,
    )

    private val _state = MutableStateFlow(State())
    internal val state = _state.asStateFlow()
    private val _events = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    internal val events = _events.asSharedFlow()
    private val recorder = VoiceRecorder(context, viewModelScope)
    private val floor = GroupPttFloor()
    private var selectedGroup: GroupEntity? = null
    private var localNodeId: String? = null
    private var activeTurn: String? = null
    private var ticker: Job? = null
    private var groupId: String? = null

    fun open(group: String) {
        if (groupId == group) return
        groupId = group
        viewModelScope.launch {
            val row = groups.find(group)
            val self = identity.nodeId()
            localNodeId = self
            if (row == null || row.left || self !in GroupMembersStore.decode(row.members)) {
                selectedGroup = null
                _state.value = State(phase = Phase.ERROR, error = R.string.nos_group_unavailable)
                return@launch
            }
            selectedGroup = row
            _state.value = State(
                phase = Phase.READY,
                title = row.name.ifBlank { "Grupo" },
                members = GroupMembersStore.decode(row.members).size,
            )
        }
    }

    /** Must only be called AFTER Android RECORD_AUDIO permission is granted by the Composable. */
    fun press() {
        if (_state.value.phase != Phase.READY || selectedGroup == null) return
        val turn = UUID.randomUUID().toString()
        val me = localNodeId ?: return
        if (!floor.claim(me, turn, MAX_BURST_MS)) return
        if (!recorder.start()) {
            floor.release(me, turn)
            _events.tryEmit(R.string.nos_group_mic_error)
            return
        }
        activeTurn = turn
        _state.value = _state.value.copy(phase = Phase.RECORDING, elapsedMs = 0L, amplitude = 0f)
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (_state.value.phase == Phase.RECORDING) {
                delay(100)
                val duration = recorder.elapsedMs()
                _state.value = _state.value.copy(elapsedMs = duration, amplitude = recorder.amplitude())
                if (duration >= MAX_BURST_MS) {
                    release()
                    break
                }
            }
        }
    }

    fun release() {
        if (_state.value.phase != Phase.RECORDING) return
        ticker?.cancel()
        ticker = null
        finishTurn()
        val duration = recorder.elapsedMs()
        _state.value = _state.value.copy(phase = Phase.SENDING, amplitude = 0f)
        viewModelScope.launch {
            try {
                // Avoid sending slipped-finger clicks. Still stop/release the hardware microphone.
                if (duration < MIN_BURST_MS) {
                    recorder.cancel()
                    _events.emit(R.string.nos_group_too_short)
                    return@launch
                }
                val bytes = recorder.stop()
                if (bytes == null || (VoiceAudio.durationMs(bytes) ?: 0) < MIN_BURST_MS) {
                    _events.emit(R.string.nos_group_mic_error)
                    return@launch
                }
                val current = groupId?.let { groups.find(it) }
                if (current == null || current.left || identity.nodeId() !in GroupMembersStore.decode(current.members)) {
                    _events.emit(R.string.nos_group_unavailable)
                    return@launch
                }
                val result = attachments.ingestVoice(bytes)
                val ingested = (result as? AttachmentStore.IngestResult.Success)?.ingested
                if (ingested == null) {
                    _events.emit(R.string.nos_group_send_error)
                    return@launch
                }
                // sendChat encrypts, stores and floods/queues this audio with Knit's normal group semantics.
                // Does not claim live delivery; peers may receive it later after reconnection.
                val queued = mesh.sendChat(
                    text = "",
                    attachment = ingested,
                    mentions = emptyList(),
                    recipientId = null,
                    group = current.toGroupInfo(),
                    replyTo = null,
                )
                _events.emit(if (queued) R.string.nos_group_queued else R.string.nos_group_send_error)
            } catch (cancelled: CancellationException) {
                recorder.cancel()
                throw cancelled
            } catch (_: Exception) {
                _events.emit(R.string.nos_group_send_error)
            } finally {
                val previous = _state.value
                _state.value = previous.copy(phase = Phase.READY, elapsedMs = 0L, amplitude = 0f)
            }
        }
    }

    fun cancel() {
        if (_state.value.phase != Phase.RECORDING) return
        ticker?.cancel()
        ticker = null
        recorder.cancel()
        finishTurn()
        _state.value = _state.value.copy(phase = Phase.READY, elapsedMs = 0L, amplitude = 0f)
    }

    private fun finishTurn() {
        activeTurn?.let { turn -> localNodeId?.let { floor.release(it, turn) } }
        activeTurn = null
    }

    override fun onCleared() {
        ticker?.cancel()
        recorder.cancel()
        finishTurn()
        super.onCleared()
    }

    companion object {
        private const val MIN_BURST_MS = 350
        private const val MAX_BURST_MS = 12_000L
    }
}
