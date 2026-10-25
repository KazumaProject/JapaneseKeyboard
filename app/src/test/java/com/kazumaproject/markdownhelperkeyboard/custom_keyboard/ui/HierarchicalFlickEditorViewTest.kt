package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.ui

import android.content.DialogInterface
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.custom_keyboard.data.TfbiFlickNode
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection.*
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data.FlickDirectionMapper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HierarchicalFlickEditorViewTest {
    private fun View.descendants(): List<View> = listOf(this) +
        if (this is ViewGroup) (0 until childCount).flatMap { getChildAt(it).descendants() } else emptyList()

    @Test fun editThreeStagesUsingTheVisibleGridAndDialogs() {
        val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext(),
            com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar)
        val view = HierarchicalFlickEditorView(context)
        view.setDefinition(TfbiFlickNode.StatefulKey(mapOf(TAP to TfbiFlickNode.Input("base")), label = "key"))
        var changes = 0
        view.onDefinitionChanged = { changes++ }
        fun click(direction: com.kazumaproject.custom_keyboard.view.TfbiFlickDirection) {
            view.descendants().filterIsInstance<Button>().single {
                it.text.toString().lineSequence().first() == FlickDirectionMapper.toDisplayName(direction, context)
            }.performClick()
        }
        fun choose(textRes: Int) {
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            val list = dialog.listView
            val index = (0 until list.adapter.count).single { list.adapter.getItem(it) == context.getString(textRes) }
            list.performItemClick(null, index, index.toLong())
        }
        click(RIGHT); choose(R.string.three_stage_add_branch)
        click(DOWN); choose(R.string.three_stage_add_branch)
        click(LEFT); choose(R.string.three_stage_output)
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        dialog.window!!.decorView.descendants().filterIsInstance<EditText>().single().setText("third😀")
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        val stage2 = view.definition.normalMap[RIGHT] as TfbiFlickNode.SubMenu
        val stage3 = stage2.nextMap[DOWN] as TfbiFlickNode.SubMenu
        assertEquals(TfbiFlickNode.Input("third😀"), stage3.nextMap[LEFT])
        assertEquals(3, changes)
        assertTrue(view.hasOutput())
        click(UP)
        val choices = (ShadowDialog.getLatestDialog() as AlertDialog).listView.adapter
        assertFalse((0 until choices.count).any { choices.getItem(it) == context.getString(R.string.three_stage_add_branch) })
    }
}
