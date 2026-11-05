package app.getknit.knit.ui.radiohub

private fun checkCase(name: String, block: () -> Unit) {
    block()
    println("OK: $name")
}

fun main() {
    val t0 = 1_780_000_000_000L
    var now = t0
    val floor = GroupPttFloor { now }
    val signal = GroupPttProtocol.Signal("group-1", "alice", "turn-a", GroupPttProtocol.Kind.CLAIM, t0, 10_000L)
    checkCase("codec v1 round-trip") { check(GroupPttProtocol.decode(GroupPttProtocol.encode(signal)!!) == signal) }
    checkCase("truncated packet rejected") { check(GroupPttProtocol.decode(GroupPttProtocol.encode(signal)!!.dropLast(1).toByteArray()) == null) }
    checkCase("unknown wire version rejected") {
        val wire = GroupPttProtocol.encode(signal)!!
        wire[4] = 99
        check(GroupPttProtocol.decode(wire) == null)
    }
    checkCase("oversized frame rejected") { check(GroupPttProtocol.decode(ByteArray(600)) == null) }
    checkCase("lease cap enforced") { check(GroupPttProtocol.encode(signal.copy(leaseMs = 30_000)) == null) }
    checkCase("one local microphone holder") {
        check(floor.claim("alice", "a", 1000))
        check(!floor.claim("bob", "b", 1000))
        check(!floor.release("bob", "b"))
        check(floor.release("alice", "a"))
    }
    checkCase("expired local lease freed") {
        check(floor.claim("alice", "a", 1000))
        now += 1001
        check(floor.claim("bob", "b", 1000))
        check(floor.release("bob", "b"))
    }
    checkCase("authenticated remote member and anti-replay") {
        val message = signal.copy(createdAtMs = now)
        check(!floor.acceptRemote(message, authenticatedSender = "mallory", members = setOf("alice")))
        check(!floor.acceptRemote(message, authenticatedSender = "alice", members = setOf("bob")))
        check(floor.acceptRemote(message, authenticatedSender = "alice", members = setOf("alice", "bob")))
        check(!floor.acceptRemote(message, authenticatedSender = "alice", members = setOf("alice", "bob")))
        check(!floor.acceptRemote(message.copy(senderId = "bob", turnId = "turn-b"), authenticatedSender = "bob", members = setOf("alice", "bob")))
        val release = message.copy(kind = GroupPttProtocol.Kind.RELEASE)
        check(floor.acceptRemote(release, authenticatedSender = "alice", members = setOf("alice", "bob")))
    }
    checkCase("future clock and stale signal rejected") {
        check(!floor.acceptRemote(signal.copy(createdAtMs = now + 7_000), "alice", setOf("alice")))
        check(!floor.acceptRemote(signal.copy(createdAtMs = now - 30_000), "alice", setOf("alice")))
    }
    println("9 core scenarios passed. Does not test remote transport, Android UI or live media.")
}
