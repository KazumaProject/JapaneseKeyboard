package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.Context
import android.view.MenuItem
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KeyboardSelectionEntryPointTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun keyboardSelectionCanBeFoundByCommonAliasesInNewAndLegacySearch() {
        val aliases = listOf(
            "choose layout",
            "select keyboard layout",
            "キーボードを適用",
            "レイアウトを選ぶ",
        )

        SettingSearchScope.entries.forEach { scope ->
            val destinations = SettingSearchIndex.searchable(context, scope)

            aliases.forEach { alias ->
                val resultKeys = SettingSearchIndex.search(context, destinations, alias)
                    .map { it.key }

                assertTrue(
                    "Missing keyboard selection for '$alias' in $scope",
                    "keyboard_selection_preference" in resultKeys,
                )
            }
        }
    }

    @Test
    fun customKeyboardHeaderLinksToKeyboardSelectionWithAnIfRoomAction() {
        val actionId = context.resources.getIdentifier(
            "action_select_keyboard_layouts",
            "id",
            context.packageName,
        )
        assertTrue("Missing custom-keyboard header action", actionId != 0)

        val menuItem = xmlElements(R.menu.keyboard_list_menu, "item")
            .firstOrNull { it.androidId == actionId }
            ?: error("Missing action_select_keyboard_layouts from keyboard_list_menu")

        assertEquals(R.string.keyboard_selection_preference_title, menuItem.androidTitle)
        assertEquals(
            context.resources.getIdentifier("keyboard_24px", "drawable", context.packageName),
            menuItem.androidIcon,
        )
        assertEquals(MenuItem.SHOW_AS_ACTION_IF_ROOM, menuItem.showAsAction)

        val navAction = xmlElements(R.navigation.mobile_navigation, "action")
            .firstOrNull { it.androidId == actionId }
            ?: error("Missing action_select_keyboard_layouts from mobile_navigation")

        assertEquals(R.id.keyboardSelectionFragment, navAction.destination)
    }

    @Test
    fun customKeyboardBackupActionsUseCustomKeyboardLabels() {
        val menuItems = xmlElements(R.menu.keyboard_list_menu, "item")

        assertEquals(
            R.string.custom_keyboard_export_title,
            menuItems.first { it.androidId == R.id.action_export_layouts }.androidTitle,
        )
        assertEquals(
            R.string.custom_keyboard_import_title,
            menuItems.first { it.androidId == R.id.action_import_layouts }.androidTitle,
        )
    }

    private fun xmlElements(xmlRes: Int, tagName: String): List<XmlElement> {
        val parser = context.resources.getXml(xmlRes)
        return parser.use {
            buildList {
                while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType == XmlPullParser.START_TAG && parser.name == tagName) {
                        add(
                            XmlElement(
                                androidId = parser.getAttributeResourceValue(ANDROID_NS, "id", 0),
                                androidTitle = parser.getAttributeResourceValue(ANDROID_NS, "title", 0),
                                androidIcon = parser.getAttributeResourceValue(ANDROID_NS, "icon", 0),
                                showAsAction = parser.getAttributeIntValue(APP_NS, "showAsAction", 0),
                                destination = parser.getAttributeResourceValue(APP_NS, "destination", 0),
                            )
                        )
                    }
                    parser.next()
                }
            }
        }
    }

    private data class XmlElement(
        val androidId: Int,
        val androidTitle: Int,
        val androidIcon: Int,
        val showAsAction: Int,
        val destination: Int,
    )

    private companion object {
        private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
        private const val APP_NS = "http://schemas.android.com/apk/res-auto"
    }
}
