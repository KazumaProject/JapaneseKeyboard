package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_theme

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.button.MaterialButtonToggleGroup
import com.kazumaproject.core.data.keyboard.KeyboardSkinId
import com.kazumaproject.core.data.keyboard.KeyboardSkinMotionMode
import com.kazumaproject.core.data.keyboard.KeyboardSkinPreviewView
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class KeyboardSkinPickerFragment : Fragment(R.layout.fragment_keyboard_skin_picker) {

    @Inject
    lateinit var appPreference: AppPreference

    private lateinit var adapter: SkinAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val selectedSkin = KeyboardSkinId.fromPreference(appPreference.keyboard_skin)
        val motionMode = KeyboardSkinMotionMode.fromPreference(appPreference.keyboard_skin_motion)
        adapter = SkinAdapter(selectedSkin, motionMode) { skin ->
            appPreference.keyboard_skin = skin.preferenceValue
        }

        view.findViewById<RecyclerView>(R.id.keyboard_skin_grid).apply {
            layoutManager = GridLayoutManager(requireContext(), columnCount())
            adapter = this@KeyboardSkinPickerFragment.adapter
            itemAnimator = null
            setHasFixedSize(true)
        }

        val motionGroup =
            view.findViewById<MaterialButtonToggleGroup>(R.id.keyboard_skin_motion_group)
        motionGroup.check(buttonIdFor(motionMode))
        motionGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val selectedMotion = motionForButtonId(checkedId)
            appPreference.keyboard_skin_motion = selectedMotion.preferenceValue
            adapter.updateMotion(selectedMotion)
        }
    }

    private fun buttonIdFor(mode: KeyboardSkinMotionMode): Int = when (mode) {
        KeyboardSkinMotionMode.FULL -> R.id.keyboard_skin_motion_full
        KeyboardSkinMotionMode.REDUCED -> R.id.keyboard_skin_motion_reduced
        KeyboardSkinMotionMode.OFF -> R.id.keyboard_skin_motion_off
    }

    private fun motionForButtonId(buttonId: Int): KeyboardSkinMotionMode = when (buttonId) {
        R.id.keyboard_skin_motion_reduced -> KeyboardSkinMotionMode.REDUCED
        R.id.keyboard_skin_motion_off -> KeyboardSkinMotionMode.OFF
        else -> KeyboardSkinMotionMode.FULL
    }

    private inner class SkinAdapter(
        private var selectedSkin: KeyboardSkinId,
        private var motionMode: KeyboardSkinMotionMode,
        private val onSelected: (KeyboardSkinId) -> Unit,
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private val skins = KeyboardSkinId.entries

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val itemView = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_keyboard_skin, parent, false)
            return SkinViewHolder(itemView)
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            (holder as SkinViewHolder).bind(skins[position])
        }

        override fun getItemCount(): Int = skins.size

        fun updateMotion(value: KeyboardSkinMotionMode) {
            if (motionMode == value) return
            motionMode = value
            notifyItemChanged(skins.indexOf(selectedSkin))
        }

        private fun select(skin: KeyboardSkinId) {
            if (skin == selectedSkin) return
            val previous = skins.indexOf(selectedSkin)
            selectedSkin = skin
            onSelected(skin)
            notifyItemChanged(previous)
            notifyItemChanged(skins.indexOf(skin))
        }

        inner class SkinViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val card = itemView.findViewById<MaterialCardView>(R.id.keyboard_skin_card)
            private val preview =
                itemView.findViewById<KeyboardSkinPreviewView>(R.id.keyboard_skin_preview)
            private val name = itemView.findViewById<TextView>(R.id.keyboard_skin_name)
            private val material = itemView.findViewById<TextView>(R.id.keyboard_skin_material)
            private val badge = itemView.findViewById<View>(R.id.keyboard_skin_selected_badge)

            fun bind(skin: KeyboardSkinId) {
                val isSelected = skin == selectedSkin
                name.setText(nameResource(skin))
                material.setText(materialResource(skin))
                badge.isVisible = isSelected
                preview.setSkin(
                    skin,
                    if (isSelected) motionMode else KeyboardSkinMotionMode.OFF,
                )

                val selectedColor = MaterialColors.getColor(
                    card,
                    androidx.appcompat.R.attr.colorPrimary,
                )
                val outlineColor = MaterialColors.getColor(
                    card,
                    com.google.android.material.R.attr.colorOutline,
                )
                card.strokeColor = if (isSelected) selectedColor else outlineColor
                card.strokeWidth = resources.displayMetrics.density
                    .times(if (isSelected) 3f else 1f)
                    .toInt()
                    .coerceAtLeast(1)
                card.contentDescription = getString(
                    R.string.keyboard_skin_accessibility_description,
                    getString(nameResource(skin)),
                    getString(materialResource(skin)),
                    if (isSelected) getString(R.string.keyboard_skin_selected) else "",
                )
                card.setOnClickListener { select(skin) }
            }
        }
    }

    private fun nameResource(skin: KeyboardSkinId): Int = when (skin) {
        KeyboardSkinId.DEFAULT -> R.string.keyboard_skin_default
        KeyboardSkinId.FLAT -> R.string.keyboard_skin_flat
        KeyboardSkinId.GLASS -> R.string.keyboard_skin_glass
        KeyboardSkinId.NEUMORPHISM -> R.string.keyboard_skin_neumorphism
        KeyboardSkinId.MECHANICAL -> R.string.keyboard_skin_mechanical
        KeyboardSkinId.WASHI -> R.string.keyboard_skin_washi
        KeyboardSkinId.NEON -> R.string.keyboard_skin_neon
        KeyboardSkinId.TERMINAL -> R.string.keyboard_skin_terminal
        KeyboardSkinId.CUPERTINO -> R.string.keyboard_skin_cupertino
        KeyboardSkinId.CUPERTINO_DARK -> R.string.keyboard_skin_cupertino_dark
        KeyboardSkinId.SUMI_HANSHI -> R.string.keyboard_skin_sumi_hanshi
        KeyboardSkinId.LETTERPRESS -> R.string.keyboard_skin_letterpress
        KeyboardSkinId.PORCELAIN -> R.string.keyboard_skin_porcelain
        KeyboardSkinId.URUSHI -> R.string.keyboard_skin_urushi
        KeyboardSkinId.CHALKBOARD -> R.string.keyboard_skin_chalkboard
        KeyboardSkinId.LINEN -> R.string.keyboard_skin_linen
        KeyboardSkinId.MONOCHROME_LCD -> R.string.keyboard_skin_monochrome_lcd
    }

    private fun materialResource(skin: KeyboardSkinId): Int = when (skin) {
        KeyboardSkinId.DEFAULT -> R.string.keyboard_skin_material_default
        KeyboardSkinId.FLAT -> R.string.keyboard_skin_material_flat
        KeyboardSkinId.GLASS -> R.string.keyboard_skin_material_glass
        KeyboardSkinId.NEUMORPHISM -> R.string.keyboard_skin_material_neumorphism
        KeyboardSkinId.MECHANICAL -> R.string.keyboard_skin_material_mechanical
        KeyboardSkinId.WASHI -> R.string.keyboard_skin_material_washi
        KeyboardSkinId.NEON -> R.string.keyboard_skin_material_neon
        KeyboardSkinId.TERMINAL -> R.string.keyboard_skin_material_terminal
        KeyboardSkinId.CUPERTINO -> R.string.keyboard_skin_material_cupertino
        KeyboardSkinId.CUPERTINO_DARK -> R.string.keyboard_skin_material_cupertino_dark
        KeyboardSkinId.SUMI_HANSHI -> R.string.keyboard_skin_material_sumi_hanshi
        KeyboardSkinId.LETTERPRESS -> R.string.keyboard_skin_material_letterpress
        KeyboardSkinId.PORCELAIN -> R.string.keyboard_skin_material_porcelain
        KeyboardSkinId.URUSHI -> R.string.keyboard_skin_material_urushi
        KeyboardSkinId.CHALKBOARD -> R.string.keyboard_skin_material_chalkboard
        KeyboardSkinId.LINEN -> R.string.keyboard_skin_material_linen
        KeyboardSkinId.MONOCHROME_LCD -> R.string.keyboard_skin_material_monochrome_lcd
    }

    private fun columnCount(): Int = if (resources.configuration.screenWidthDp >= TABLET_MIN_WIDTH_DP) {
        TABLET_COLUMN_COUNT
    } else {
        PHONE_COLUMN_COUNT
    }

    companion object {
        private const val PHONE_COLUMN_COUNT = 2
        private const val TABLET_COLUMN_COUNT = 3
        private const val TABLET_MIN_WIDTH_DP = 600
    }
}
