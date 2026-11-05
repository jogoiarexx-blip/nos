package app.getknit.knit.ui.contacts

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupPickerPolicyTest {
    @Test fun `empty selection cannot be confirmed`() {
        assertFalse(groupPickerCanConfirm(0, groupOnly = true))
        assertFalse(groupPickerCanConfirm(0, groupOnly = false))
    }

    @Test fun `group-only selection requires two other people`() {
        assertFalse(groupPickerCanConfirm(1, groupOnly = true))
        assertTrue(groupPickerCanConfirm(2, groupOnly = true))
        assertTrue(groupPickerCanConfirm(7, groupOnly = true))
    }

    @Test fun `normal chat picker may still open a dm`() {
        assertTrue(groupPickerCanConfirm(1, groupOnly = false))
    }
}
