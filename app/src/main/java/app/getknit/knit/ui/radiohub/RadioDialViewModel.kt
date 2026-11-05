package app.getknit.knit.ui.radiohub

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.getknit.knit.R
import app.getknit.knit.call.CallKind
import app.getknit.knit.call.CallManager
import app.getknit.knit.call.CallRefusal
import app.getknit.knit.data.PeerRepository
import app.getknit.knit.data.settings.SettingsStore
import app.getknit.knit.identity.Identity
import app.getknit.knit.mesh.protocol.Protocol
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Pure, testable pre-flight gate; a profile's capabilities are claims, not a guarantee of reachability. */
internal enum class RadioDialBlock {
    INVALID_PEER,
    SELF,
    BLOCKED,
    NOT_SUPPORTED,
    DISABLED,
}

internal fun radioDialBlock(
    peerId: String,
    selfId: String,
    blocked: Set<String>,
    capabilities: Long?,
    walkieEnabled: Boolean,
): RadioDialBlock? =
    when {
        peerId.isBlank() -> RadioDialBlock.INVALID_PEER
        peerId == selfId -> RadioDialBlock.SELF
        peerId in blocked -> RadioDialBlock.BLOCKED
        !walkieEnabled -> RadioDialBlock.DISABLED
        capabilities == null -> RadioDialBlock.INVALID_PEER
        (capabilities and Protocol.CAP_WALKIE) == 0L -> RadioDialBlock.NOT_SUPPORTED
        else -> null
    }

/** One-shot feedback; the call UI is still owned by Knit's existing call controller. */
internal data class RadioDialEvent(
    @param:StringRes val message: Int,
    val started: Boolean,
)

/**
 * Places only a verified *kind* of call (WALKIE), after checking the pinned peer profile and local blocklist.
 * It never falls back to a live/Internet call, never treats a group as a voice channel, and never bypasses
 * CallManager's busy/telecom rules. Existing Knit wire protocol and encryption remain untouched.
 */
class RadioDialViewModel(
    private val peers: PeerRepository,
    private val settings: SettingsStore,
    private val identity: Identity,
    private val calls: CallManager?,
) : ViewModel() {
    private val _events = MutableSharedFlow<RadioDialEvent>(extraBufferCapacity = 1)
    internal val events: SharedFlow<RadioDialEvent> = _events.asSharedFlow()

    private val _dialingPeer = MutableStateFlow<String?>(null)
    internal val dialingPeer: StateFlow<String?> = _dialingPeer.asStateFlow()

    fun dial(peerId: String) {
        // Prevent a double-tap opening two calls before the network result arrives.
        if (_dialingPeer.value != null) return
        _dialingPeer.value = peerId
        viewModelScope.launch {
            try {
                val block =
                    radioDialBlock(
                        peerId = peerId,
                        selfId = identity.nodeId(),
                        blocked = settings.blockedNodeIds.first(),
                        capabilities = peers.find(peerId)?.capabilities,
                        walkieEnabled = calls?.walkieEnabled == true,
                    )
                if (block != null) {
                    _events.emit(RadioDialEvent(block.message(), started = false))
                    return@launch
                }
                // ProfileDetailsViewModel does the same explicit acceptance before a user-initiated call.
                // This point is reached only after the block/capability checks.
                settings.accept(peerId)
                val outcome = calls?.start(peerId, CallKind.WALKIE)
                _events.emit(
                    if (outcome == null && calls != null) {
                        RadioDialEvent(R.string.radio_hub_call_started, started = true)
                    } else {
                        RadioDialEvent(outcome.message(), started = false)
                    },
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _events.emit(RadioDialEvent(R.string.radio_hub_call_failed, started = false))
            } finally {
                _dialingPeer.value = null
            }
        }
    }
}

@StringRes
private fun RadioDialBlock.message(): Int =
    when (this) {
        RadioDialBlock.INVALID_PEER -> R.string.radio_hub_call_invalid
        RadioDialBlock.SELF -> R.string.chat_call_needs_dm
        RadioDialBlock.BLOCKED -> R.string.radio_hub_call_blocked
        RadioDialBlock.NOT_SUPPORTED -> R.string.chat_mesh_call_peer_too_old
        RadioDialBlock.DISABLED -> R.string.radio_hub_call_disabled
    }

@StringRes
private fun CallRefusal?.message(): Int =
    when (this) {
        CallRefusal.BUSY -> R.string.chat_call_busy
        CallRefusal.SELF -> R.string.chat_call_needs_dm
        CallRefusal.NO_LIVE -> R.string.chat_mesh_call_peer_too_old
        CallRefusal.NO_RELAY -> R.string.chat_call_no_relay
        null -> R.string.radio_hub_call_failed
    }
