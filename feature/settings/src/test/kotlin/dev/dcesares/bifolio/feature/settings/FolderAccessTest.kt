package dev.dcesares.bifolio.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.dcesares.bifolio.designsystem.BifolioTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** PRV-04: granted folders are listed with a revoke action, and an empty state when there are none. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FolderAccessTest {
    @get:Rule
    val compose = createComposeRule()

    private val notes = "content://com.android.externalstorage.documents/tree/primary%3ADocuments%2FNotes"
    private val root = "content://com.android.externalstorage.documents/tree/primary%3A"

    private fun show(
        trees: List<String>,
        onRevoke: (String) -> Unit = {},
    ) = compose.setContent { BifolioTheme { FolderAccess(trees, onRevoke) } }

    @Test
    fun listsEachFolderByName() {
        show(listOf(notes, root))
        compose.onNodeWithText("Notes").assertIsDisplayed()
        compose.onNodeWithText("Storage root").assertIsDisplayed()
        compose.onNodeWithContentDescription("Revoke access to Notes").assertIsDisplayed()
    }

    @Test
    fun emptyStateWhenNoFolders() {
        show(emptyList())
        compose.onNodeWithText("No folders have been allowed.").assertIsDisplayed()
    }

    @Test
    fun revokeReportsTheTree() {
        val revoked = mutableListOf<String>()
        show(listOf(notes, root)) { revoked += it }
        compose.onNodeWithContentDescription("Revoke access to Notes").performClick()
        assertEquals(listOf(notes), revoked)
    }
}
