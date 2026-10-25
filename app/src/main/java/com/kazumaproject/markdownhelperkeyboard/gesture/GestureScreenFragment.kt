package com.kazumaproject.markdownhelperkeyboard.gesture

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton

abstract class GestureScreenFragment : Fragment() {
    protected lateinit var content: LinearLayout
    protected val store get() = GesturePreferences(requireContext())
    protected val ja get() = resources.configuration.locales[0].language == "ja"
    protected fun t(japanese: String, english: String) = if (ja) japanese else english
    protected fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        content = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(24)) }
        build(savedInstanceState)
        return ScrollView(requireContext()).apply { addView(content) }
    }
    protected abstract fun build(state: Bundle?)
    protected fun text(label: String) = TextView(requireContext()).apply { text = label; textSize = 16f; setPadding(0, dp(8), 0, dp(8)); content.addView(this) }
    protected fun button(label: String, action: () -> Unit): MaterialButton = MaterialButton(requireContext()).apply {
        text = label; isAllCaps = false; setOnClickListener { action() }; content.addView(this)
    }
    protected fun attempt(action: () -> Unit): Boolean = runCatching(action).onFailure {
        Toast.makeText(requireContext(), it.message ?: t("処理できませんでした", "Unable to complete operation"), Toast.LENGTH_LONG).show()
    }.isSuccess
}
