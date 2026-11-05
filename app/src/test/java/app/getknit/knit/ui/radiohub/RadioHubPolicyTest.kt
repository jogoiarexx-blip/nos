package app.getknit.knit.ui.radiohub

import app.getknit.knit.ui.chatlist.ConversationRow
import org.junit.Assert.assertEquals
import org.junit.Test

class RadioHubPolicyTest {
    private fun conversation(
        id: String,
        isRoom: Boolean = false,
        isGroup: Boolean = false,
    ) = ConversationRow(
        id = id,
        title = id,
        avatarHash = null,
        isRoom = isRoom,
        isGroup = isGroup,
        lastPreview = null,
        lastMessageAt = null,
        unreadCount = 0,
    )

    @Test
    fun `only private conversations are offered as walkie entry points`() {
        val conversations =
            listOf(
                conversation("Nearby", isRoom = true),
                conversation("Alice"),
                conversation("Team", isGroup = true),
                conversation("Bob"),
                conversation("LoRa", isRoom = true),
            )

        assertEquals(listOf("Alice", "Bob"), radioPeers(conversations).map { it.id })
    }

    @Test
    fun `only actual group threads appear as recorded group channels`() {
        val conversations = listOf(
            conversation("Nearby", isRoom = true),
            conversation("Team", isGroup = true),
            conversation("Mixed", isRoom = true, isGroup = true),
            conversation("Alice"),
            conversation("Family", isGroup = true),
        )
        assertEquals(listOf("Family", "Team"), radioGroupThreads(conversations).map { it.id })
    }

    @Test
    fun `unread group chats are listed first, then most recently active`() {
        val rows = listOf(
            conversation("old", isGroup = true).copy(lastMessageAt = 10),
            conversation("new", isGroup = true).copy(lastMessageAt = 30),
            conversation("unread", isGroup = true).copy(unreadCount = 2, lastMessageAt = 1),
        )
        assertEquals(listOf("unread", "new", "old"), radioGroupThreads(rows).map { it.id })
    }

    @Test
    fun `empty conversation list offers no walkie recipients`() {
        assertEquals(emptyList<ConversationRow>(), radioPeers(emptyList()))
    }
}
