package app.getknit.knit.ui.radiohub

import app.getknit.knit.ui.chatlist.ConversationRow

/** Only real group chats can carry recorded voice clips; public rooms must never look like channels. */
internal fun radioGroupThreads(conversations: List<ConversationRow>): List<ConversationRow> =
    conversations.filter { it.isGroup && !it.isRoom }
        .sortedWith(
            compareByDescending<ConversationRow> { it.unreadCount > 0 }
                .thenByDescending { it.lastMessageAt ?: Long.MIN_VALUE }
                .thenBy { it.title },
        )
