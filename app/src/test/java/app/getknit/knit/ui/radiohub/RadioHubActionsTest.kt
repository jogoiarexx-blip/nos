package app.getknit.knit.ui.radiohub

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.getknit.knit.ui.chatlist.ChatListUiState
import app.getknit.knit.ui.chatlist.ConversationRow
import app.getknit.knit.ui.theme.KnitTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RadioHubActionsTest {
    @get:Rule val compose = createComposeRule()

    private fun row(id: String, group: Boolean = false) = ConversationRow(
        id = id,
        title = id,
        avatarHash = null,
        isRoom = false,
        isGroup = group,
        lastPreview = null,
        lastMessageAt = null,
        unreadCount = 0,
    )

    private fun show(
        dialing: String? = null,
        onCall: (String) -> Unit,
        onOpen: (String) -> Unit = {},
        onCreateGroup: () -> Unit = {},
        onPtt: (String) -> Unit = {},
        onLive: () -> Unit = {},
    ) {
        compose.setContent {
            KnitTheme {
                RadioHubContent(
                    state = ChatListUiState(conversations = listOf(row("alice"), row("group", group = true))),
                    onBack = {},
                    onOpenConversation = onOpen,
                    onCallPeer = onCall,
                    onCreateGroup = onCreateGroup,
                    onOpenGroupPtt = onPtt,
                    onOpenLiveRadio = onLive,
                    dialingPeer = dialing,
                    onPickContact = {},
                    onOpenYourMesh = {},
                    onStartMesh = {},
                    onResumeMesh = {},
                )
            }
        }
    }

    @Test fun `call button starts walkie for individual conversation only`() {
        val called = mutableListOf<String>()
        show(onCall = { called += it })
        compose.onNodeWithTag("radio_hub").performScrollToIndex(8)
        compose.onNodeWithTag("radio_hub_call_alice").assertIsDisplayed().performClick()
        assertEquals(listOf("alice"), called)
        compose.onNodeWithTag("radio_hub_call_group").assertDoesNotExist()
    }

    @Test fun `group channel opens chat without placing a live walkie call`() {
        val opened = mutableListOf<String>()
        val called = mutableListOf<String>()
        show(onCall = { called += it }, onOpen = { opened += it })
        compose.onNodeWithTag("radio_hub").performScrollToIndex(5)
        compose.onNodeWithTag("radio_group_open_group").assertIsDisplayed().performClick()
        assertEquals(listOf("group"), opened)
        assertEquals(emptyList<String>(), called)
    }

    @Test fun `group fast PTT opens separate voice burst screen`() {
        val opened = mutableListOf<String>()
        val called = mutableListOf<String>()
        show(onCall = { called += it }, onPtt = { opened += it })
        compose.onNodeWithTag("radio_hub").performScrollToIndex(5)
        compose.onNodeWithTag("nos_group_fast_group").assertIsDisplayed().performClick()
        assertEquals(listOf("group"), opened)
        assertEquals(emptyList<String>(), called)
    }

    @Test fun `create group opens a group-only picker`() {
        var creates = 0
        show(onCall = {}, onCreateGroup = { creates++ })
        compose.onNodeWithTag("radio_hub").performScrollToIndex(4)
        compose.onNodeWithTag("radio_group_create").assertIsDisplayed().performClick()
        assertEquals(1, creates)
    }

    @Test fun `live local radio has its own screen action`() {
        var launches = 0
        show(onCall = {}, onLive = { launches++ })
        compose.onNodeWithTag("radio_hub").performScrollToIndex(2)
        compose.onNodeWithTag("nos_open_live_radio").assertIsDisplayed().performClick()
        assertEquals(1, launches)
    }

    @Test fun `dial button disabled while another request pending`() {
        show(dialing = "alice", onCall = {})
        compose.onNodeWithTag("radio_hub").performScrollToIndex(8)
        compose.onNodeWithTag("radio_hub_call_alice").assertIsNotEnabled()
    }
}
