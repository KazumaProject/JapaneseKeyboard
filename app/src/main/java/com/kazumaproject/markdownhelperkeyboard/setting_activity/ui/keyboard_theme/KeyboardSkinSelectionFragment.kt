package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_theme

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.textview.MaterialTextView
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class KeyboardSkinSelectionFragment : Fragment(R.layout.fragment_keyboard_skin_selection) {
    @Inject lateinit var appPreference: AppPreference

    private val landscape get() = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val list = view.findViewById<RecyclerView>(R.id.keyboard_theme_list)
        list.layoutManager = GridLayoutManager(requireContext(), if (!landscape && resources.configuration.screenWidthDp >= 600) 2 else 1)
        list.adapter = ThemeAdapter()
        view.findViewById<View>(R.id.keyboard_theme_preview_note).visibility = if (landscape) View.GONE else View.VISIBLE
    }

    override fun onDestroyView() {
        view?.findViewById<RecyclerView>(R.id.keyboard_theme_list)?.adapter = null
        super.onDestroyView()
    }

    private inner class ThemeAdapter : RecyclerView.Adapter<ThemeHolder>() {
        override fun getItemCount() = KeyboardThemeCatalog.options.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThemeHolder {
            val card = MaterialCardView(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(dp(8), dp(6), dp(8), dp(6)) }
                radius = dp(18).toFloat()
                cardElevation = 0f
                isClickable = true
                isFocusable = true
            }
            val content = LinearLayout(parent.context).apply {
                orientation = if (landscape) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(14), dp(14), dp(14))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            }
            card.addView(content)
            return ThemeHolder(card, content)
        }

        override fun onBindViewHolder(holder: ThemeHolder, position: Int) {
            val option = KeyboardThemeCatalog.options[position]
            val selected = appPreference.keyboardSkin == option.id
            val context = holder.card.context
            holder.content.removeAllViews()
            holder.content.addView(KeyboardThemePreviewView(context, option.id), LinearLayout.LayoutParams(
                if (landscape) dp(200) else ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            val details = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                if (landscape) setPadding(dp(18), 0, 0, 0)
            }
            holder.content.addView(details, if (landscape)
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            else LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            fun label(text: String, size: Float): TextView = MaterialTextView(context).apply {
                this.text = text
                textSize = size
                setPadding(0, dp(8), 0, 0)
            }
            details.addView(label(getString(option.titleRes), 18f))
            details.addView(label(getString(option.descriptionRes), 14f))
            details.addView(label("✓ ${getString(R.string.keyboard_skin_selected)}", 14f).apply {
                visibility = if (selected) View.VISIBLE else View.INVISIBLE
                setTextColor(MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary))
            })
            holder.card.strokeWidth = dp(if (selected) 2 else 1)
            holder.card.strokeColor = MaterialColors.getColor(holder.card,
                if (selected) androidx.appcompat.R.attr.colorPrimary else com.google.android.material.R.attr.colorOutline)
            holder.card.isSelected = selected
            holder.card.contentDescription = "${getString(option.titleRes)}。${getString(option.descriptionRes)}"
            ViewCompat.setStateDescription(holder.card, if (selected) getString(R.string.keyboard_skin_selected) else null)
            holder.card.setOnClickListener {
                val oldIndex = KeyboardThemeCatalog.options.indexOfFirst { it.id == appPreference.keyboardSkin }
                if (appPreference.keyboardSkin != option.id) {
                    appPreference.keyboardSkin = option.id
                    if (oldIndex >= 0) notifyItemChanged(oldIndex)
                    notifyItemChanged(position)
                }
            }
        }
    }

    private class ThemeHolder(val card: MaterialCardView, val content: LinearLayout) : RecyclerView.ViewHolder(card)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
