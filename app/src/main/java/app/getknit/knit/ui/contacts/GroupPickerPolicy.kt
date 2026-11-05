package app.getknit.knit.ui.contacts

/** A radio group requires two other people. The standard new-chat picker still permits DMs. */
internal fun groupPickerCanConfirm(selectedCount: Int, groupOnly: Boolean): Boolean =
    selectedCount > 0 && (!groupOnly || selectedCount >= 2)
