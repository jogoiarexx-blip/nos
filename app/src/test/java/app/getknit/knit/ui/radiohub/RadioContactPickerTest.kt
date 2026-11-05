package app.getknit.knit.ui.radiohub

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.getknit.knit.ui.contacts.Contact
import app.getknit.knit.ui.contacts.ContactsScreenContent
import app.getknit.knit.ui.contacts.ContactsUiState
import app.getknit.knit.ui.theme.KnitTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RadioContactPickerTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `radio contact picker always dials just one person`() {
        val picked = mutableListOf<String>()
        val createdGroups = mutableListOf<List<String>>()
        compose.setContent {
            KnitTheme {
                ContactsScreenContent(
                    state = ContactsUiState(
                        contacts = listOf(
                            Contact("ada", "Ada", null, true),
                            Contact("bob", "Bob", null, true),
                        ),
                    ),
                    onBack = {},
                    onPickSingle = { picked += it },
                    onCreateGroup = { createdGroups += it },
                    onGroupFull = {},
                    singleSelectionOnly = true,
                )
            }
        }
        compose.onNodeWithTag("contact_ada").performClick()
        compose.onNodeWithTag("contact_bob").performClick()
        compose.onNodeWithTag("contacts_fab").performClick()
        assertEquals(listOf("bob"), picked)
        assertTrue(createdGroups.isEmpty())
    }
    @Test fun `group-only picker never opens a dm with one selected peer`() {
        val picked = mutableListOf<String>()
        val created = mutableListOf<List<String>>()
        compose.setContent {
            KnitTheme {
                ContactsScreenContent(
                    state = ContactsUiState(
                        contacts = listOf(
                            Contact("ada", "Ada", null, true),
                            Contact("bob", "Bob", null, true),
                        ),
                    ),
                    onBack = {},
                    onPickSingle = { picked += it },
                    onCreateGroup = { created += it },
                    onGroupFull = {},
                    groupOnly = true,
                )
            }
        }
        compose.onNodeWithTag("contact_ada").performClick()
        compose.onNodeWithTag("contacts_fab").assertDoesNotExist()
        compose.onNodeWithTag("contact_bob").performClick()
        compose.onNodeWithTag("contacts_fab").performClick()
        assertTrue(picked.isEmpty())
        assertEquals(listOf(listOf("ada", "bob")), created)
    }
}
