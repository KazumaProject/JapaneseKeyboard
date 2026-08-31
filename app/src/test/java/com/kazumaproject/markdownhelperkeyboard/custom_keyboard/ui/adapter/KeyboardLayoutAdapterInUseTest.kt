package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.adapter

import android.view.ContextThemeWrapper
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.CustomKeyboardLayout
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui.KeyboardLayoutListItem
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowPopupMenu

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KeyboardLayoutAdapterInUseTest {

    @Test
    fun inUseLayoutShowsBadgeAndDisablesDeleteMenuItem() {
        val holder = holder()
        holder.bind(KeyboardLayoutListItem(layout(), isInUse = true))

        val badgeId = holder.itemView.resources.getIdentifier(
            "keyboard_in_use_badge",
            "id",
            holder.itemView.context.packageName,
        )
        assertTrue("Missing in-use badge", badgeId != 0)
        assertTrue(holder.itemView.findViewById<TextView>(badgeId).visibility == View.VISIBLE)

        holder.itemView.findViewById<View>(R.id.keyboard_menu_button).performClick()
        val popupMenu = ShadowPopupMenu.getLatestPopupMenu()
        assertFalse(popupMenu.menu.findItem(R.id.action_delete_layout).isEnabled)
    }

    @Test
    fun unusedLayoutHidesBadgeAndKeepsDeleteMenuItemEnabled() {
        val holder = holder()
        holder.bind(KeyboardLayoutListItem(layout(), isInUse = false))

        val badgeId = holder.itemView.resources.getIdentifier(
            "keyboard_in_use_badge",
            "id",
            holder.itemView.context.packageName,
        )
        assertTrue(holder.itemView.findViewById<TextView>(badgeId).visibility == View.GONE)

        holder.itemView.findViewById<View>(R.id.keyboard_menu_button).performClick()
        val popupMenu = ShadowPopupMenu.getLatestPopupMenu()
        assertTrue(popupMenu.menu.findItem(R.id.action_delete_layout).isEnabled)
    }

    private fun holder(): KeyboardLayoutAdapter.ViewHolder {
        val context = ContextThemeWrapper(
            ApplicationProvider.getApplicationContext(),
            R.style.Theme_MarkdownKeyboard,
        )
        val parent = FrameLayout(context)
        return adapter().onCreateViewHolder(parent, 0)
    }

    private fun adapter() = KeyboardLayoutAdapter(
        onItemClick = {},
        onDeleteClick = {},
        onDuplicateClick = {},
        onStartDrag = {},
    )

    private fun layout() = CustomKeyboardLayout(
        layoutId = 32L,
        name = "Active layout",
        columnCount = 5,
        rowCount = 4,
        stableId = "active-layout",
    )
}
