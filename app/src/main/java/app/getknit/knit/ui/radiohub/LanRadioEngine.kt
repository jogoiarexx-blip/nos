package app.getknit.knit.ui.radiohub

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioAttributes
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.net.wifi.WifiManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.SocketTimeoutException
import java.security.SecureRandom
import javax.crypto.spec.SecretKeySpec
import kotlin.math.max

/**
 * Best-effort live HALF-DUPLEX audio on the SAME Wi-Fi LAN/hotspot. Android Wi-Fi IP multicast is used,
 * NOT cellular, LoRa, FM/AM, Bluetooth broadcast, or Knit's multi-hop Mesh. No internet is required.
 * Many access points block multicast; test on actual devices. Network partitions can yield two talkers.
 * Runs only while the radio screen is open (no background recording / persistent microphone).
 */
internal class LanRadioEngine(context: Context) {
    data class State(
        val joined: Boolean = false,
        val busy: Boolean = false,
        val channel: String = "",
        val peers: Int = 0,
        val transmitting: Boolean = false,
        val speaker: String? = null,
        val error: String? = null,
    )

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()
    private val random = SecureRandom()
    private val senderId = random.nextLong()
    private val floor = LanRadioFloor()
    private val replay = LanRadioReplay()
    private val lastSeen = LinkedHashMap<Long, Long>()
    private val sendLock = Any()
    private val floorLock = Any()
    private val group = InetAddress.getByName(LanRadioWire.MULTICAST_ADDRESS)
    private val broadcastAddress = InetAddress.getByName("255.255.255.255")
    private var socket: MulticastSocket? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var key: SecretKeySpec? = null
    private var track: AudioTrack? = null
    private var loop: Job? = null
    private var beacon: Job? = null
    private var recordJob: Job? = null
    @Volatile private var holding = false
    @Volatile private var closed = false
    private var sequence = 0

    fun join(channel: String, password: String) {
        if (_state.value.joined || _state.value.busy || closed) return
        val normalized = LanRadioWire.normalizedChannel(channel)
        if (!LanRadioWire.validCredentials(normalized, password)) {
            _state.value = _state.value.copy(error = "Informe um canal (3–32 caracteres) e senha de pelo menos 10 caracteres.")
            return
        }
        _state.value = State(busy = true, channel = normalized)
        scope.launch {
            try {
                // Derive in the IO pool; never block the Compose UI thread with PBKDF2.
                val derived = LanRadioWire.deriveKey(normalized, password)
                if (closed) return@launch
                val wifi = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                val lock = wifi.createMulticastLock("NOS-LAN-RADIO").apply {
                    setReferenceCounted(false)
                    acquire()
                }
                multicastLock = lock
                val multi = MulticastSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                    bind(InetSocketAddress(LanRadioWire.PORT))
                    soTimeout = 750
                    timeToLive = 1
                    joinGroup(group)
                }
                socket = multi
                key = derived
                val minTrack = AudioTrack.getMinBufferSize(16_000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
                if (minTrack <= 0) error("Audio output unsupported")
                val player = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build(),
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(16_000)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .build(),
                    )
                    .setBufferSizeInBytes(max(minTrack * 2, LanRadioWire.PCM_BYTES * 6))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                if (player.state != AudioTrack.STATE_INITIALIZED) {
                    player.release()
                    error("Audio output unavailable")
                }
                track = player
                player.play()
                _state.value = State(joined = true, channel = normalized)
                loop = scope.launch { receiveLoop(multi, derived) }
                beacon = scope.launch {
                    while (isActive && _state.value.joined) {
                        send(LanRadioWire.Kind.HELLO, 0L)
                        delay(3_000)
                    }
                }
            } catch (ex: Exception) {
                closeNetwork()
                _state.value = State(error = "Não foi possível abrir o canal Wi-Fi local: ${ex.javaClass.simpleName}.")
            }
        }
    }

    fun press() {
        if (!_state.value.joined || holding || recordJob?.isActive == true || closed) return
        if (appContext.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            _state.value = _state.value.copy(error = "Permita o microfone para transmitir.")
            return
        }
        synchronized(floorLock) { if (floor.active() != null) return }
        holding = true
        recordJob = scope.launch {
            val turn = random.nextLong()
            var microphone: AudioRecord? = null
            try {
                synchronized(floorLock) { if (!floor.claim(senderId, turn)) { holding = false; return@launch } }
                send(LanRadioWire.Kind.CLAIM, turn)
                // Collision window: each device hears contenders before starting the microphone.
                delay(220)
                if (!holding || synchronized(floorLock) { floor.active()?.sender != senderId }) return@launch
                val minimum = AudioRecord.getMinBufferSize(16_000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                if (minimum <= 0) error("Audio capture unsupported")
                val recorder = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    16_000,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    max(minimum * 2, LanRadioWire.PCM_BYTES * 4),
                )
                microphone = recorder
                if (recorder.state != AudioRecord.STATE_INITIALIZED) error("Microphone unavailable")
                recorder.startRecording()
                _state.value = _state.value.copy(transmitting = true, speaker = null, error = null)
                val chunk = ByteArray(LanRadioWire.PCM_BYTES)
                val stopAfter = System.currentTimeMillis() + 30_000L
                var sentAudioFrames = 0
                while (holding && isActive && System.currentTimeMillis() < stopAfter &&
                    synchronized(floorLock) { floor.active()?.let { it.sender == senderId && it.turn == turn } == true }) {
                    var offset = 0
                    while (offset < chunk.size && holding && isActive) {
                        val read = recorder.read(chunk, offset, chunk.size - offset)
                        if (read <= 0) error("Microphone capture interrupted")
                        offset += read
                    }
                    if (offset == chunk.size) {
                        synchronized(floorLock) { floor.extend(senderId, turn) }
                        send(LanRadioWire.Kind.AUDIO, turn, chunk)
                        sentAudioFrames++
                        // Repeat the claim every 400 ms to recover if the first UDP packet was lost.
                        if (sentAudioFrames % 10 == 0) send(LanRadioWire.Kind.CLAIM, turn)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.value = _state.value.copy(error = "Falha no microfone ou na transmissão PTT.")
            } finally {
                holding = false
                try { microphone?.stop() } catch (_: Exception) { }
                microphone?.release()
                send(LanRadioWire.Kind.RELEASE, turn)
                synchronized(floorLock) { floor.release(senderId, turn) }
                _state.value = _state.value.copy(transmitting = false)
            }
        }
    }

    fun release() { holding = false }

    private fun send(kind: LanRadioWire.Kind, turn: Long, pcm: ByteArray = byteArrayOf()) {
        val current = socket ?: return
        val secret = key ?: return
        try {
            synchronized(sendLock) {
                if (sequence == Int.MAX_VALUE) return
                sequence++
                val bytes = LanRadioWire.encode(secret, LanRadioWire.Frame(kind, senderId, turn, sequence, pcm)) ?: return
                current.send(DatagramPacket(bytes, bytes.size, group, LanRadioWire.PORT))
                // Some local APs suppress multicast but pass LAN broadcast; receiver deduplicates by sequence.
                try { current.send(DatagramPacket(bytes, bytes.size, broadcastAddress, LanRadioWire.PORT)) }
                catch (_: Exception) { /* Multicast can still be usable without LAN broadcast. */ }
            }
        } catch (_: Exception) { /* network may disappear; the receiver will report lost transport */ }
    }

    private fun receiveLoop(multi: MulticastSocket, secret: SecretKeySpec) {
        val incoming = ByteArray(LanRadioWire.MAX_DATAGRAM)
        while (scope.isActive && _state.value.joined) {
            try {
                val datagram = DatagramPacket(incoming, incoming.size)
                multi.receive(datagram)
                val bytes = datagram.data.copyOfRange(datagram.offset, datagram.offset + datagram.length)
                val frame = LanRadioWire.decode(secret, bytes) ?: continue
                if (frame.sender == senderId || !replay.accept(frame)) continue
                val now = System.currentTimeMillis()
                synchronized(floorLock) {
                    lastSeen[frame.sender] = now
                    lastSeen.entries.removeAll { now - it.value > 10_000L }
                    val remote = floor.active()
                    when (frame.kind) {
                        LanRadioWire.Kind.HELLO -> Unit
                        LanRadioWire.Kind.CLAIM -> {
                            floor.claim(frame.sender, frame.turn)
                        }
                        LanRadioWire.Kind.RELEASE -> floor.release(frame.sender, frame.turn)
                        LanRadioWire.Kind.AUDIO -> {
                            if (remote?.sender == frame.sender && remote.turn == frame.turn) {
                                floor.extend(frame.sender, frame.turn)
                                if (!_state.value.transmitting) {
                                    // Stream as received: 40ms packets, no persistent voice recording.
                                    track?.write(frame.audio, 0, frame.audio.size, AudioTrack.WRITE_NON_BLOCKING)
                                }
                            }
                        }
                    }
                    val speaker = floor.active()?.takeIf { it.sender != senderId }
                    _state.value = _state.value.copy(
                        peers = lastSeen.size,
                        speaker = speaker?.sender?.toULong()?.toString(16)?.takeLast(4),
                    )
                }
            } catch (_: SocketTimeoutException) {
                val now = System.currentTimeMillis()
                synchronized(floorLock) {
                    lastSeen.entries.removeAll { now - it.value > 10_000L }
                    val speaker = floor.active()?.takeIf { it.sender != senderId }
                    _state.value = _state.value.copy(peers = lastSeen.size,
                        speaker = speaker?.sender?.toULong()?.toString(16)?.takeLast(4))
                }
            } catch (_: Exception) {
                if (_state.value.joined && !closed) {
                    _state.value = _state.value.copy(error = "Conexão Wi-Fi perdida. Saia e entre novamente no canal.")
                }
                break
            }
        }
    }

    fun leave() {
        holding = false
        closeNetwork()
        _state.value = State()
    }

    private fun closeNetwork() {
        holding = false
        recordJob?.cancel(); recordJob = null
        beacon?.cancel(); beacon = null
        loop?.cancel(); loop = null
        try { socket?.leaveGroup(group) } catch (_: Exception) { }
        try { socket?.close() } catch (_: Exception) { }
        socket = null
        try { track?.stop() } catch (_: Exception) { }
        try { track?.release() } catch (_: Exception) { }
        track = null
        if (multicastLock?.isHeld == true) multicastLock?.release()
        multicastLock = null
        key = null
        synchronized(floorLock) { floor.clear(); lastSeen.clear() }
    }

    fun close() {
        closed = true
        leave()
        scope.cancel()
    }
}
