package app.getknit.knit.ui.radiohub

fun main() {
    var checks = 0
    fun ok(value: Boolean, message: String) { check(value) { message }; checks++ }
    val key = LanRadioWire.deriveKey("geral", "canal-secreto-123")
    val same = LanRadioWire.deriveKey(" GERAL ", "canal-secreto-123")
    val wrong = LanRadioWire.deriveKey("geral", "outra-senha-123")
    val audio = ByteArray(LanRadioWire.PCM_BYTES) { (it * 11).toByte() }
    val frame = LanRadioWire.Frame(LanRadioWire.Kind.AUDIO, 34L, 88L, 9, audio)
    val encoded = LanRadioWire.encode(key, frame)!!
    ok(encoded.size <= LanRadioWire.MAX_DATAGRAM, "packet over MTU")
    ok(LanRadioWire.decode(key, encoded)?.audio?.contentEquals(audio) == true, "roundtrip pcm")
    ok(LanRadioWire.decode(same, encoded)?.turn == 88L, "same channel normalization")
    ok(LanRadioWire.decode(wrong, encoded) == null, "wrong key must fail")
    encoded[encoded.lastIndex] = (encoded.last().toInt() xor 1).toByte()
    ok(LanRadioWire.decode(key, encoded) == null, "tampering must fail")
    ok(LanRadioWire.encode(key, frame.copy(audio = ByteArray(5))) == null, "reject broken audio")
    val replay = LanRadioReplay()
    ok(replay.accept(frame), "first packet")
    ok(!replay.accept(frame), "replay rejected")
    ok(!replay.accept(frame.copy(sequence = 8)), "late packet rejected")
    ok(replay.accept(frame.copy(sequence = 10)), "next packet")
    var clock = 1000L
    val floor = LanRadioFloor { clock }
    ok(floor.claim(20, 100), "first floor")
    ok(!floor.claim(30, 200), "higher sender yields")
    ok(floor.claim(10, 300), "lower sender wins contention")
    ok(!floor.extend(20, 100), "previous sender cannot extend")
    ok(floor.extend(10, 300), "holder extends")
    floor.release(30, 200)
    ok(floor.active()?.sender == 10L, "wrong release ignored")
    clock += 1501L
    ok(floor.active() == null, "lease expired")
    ok(LanRadioWire.decode(key, byteArrayOf(1, 2, 3)) == null, "short packet rejected")
    ok(!LanRadioWire.validCredentials("geral", "123"), "weak shared secret rejected")
    println("NÓS REV6 local radio core: $checks/$checks checks OK")
}
