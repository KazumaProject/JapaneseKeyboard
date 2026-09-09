package com.kazumaproject.markdownhelperkeyboard

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.*
import android.widget.FrameLayout
import com.kazumaproject.core.domain.skin.KeyboardSkinId
import com.kazumaproject.core.ui.skin.KeyboardSkinRegistry
import com.kazumaproject.tenkey.TenKey
import com.kazumaproject.qwerty_keyboard.ui.QWERTYKeyboardView

/** Debug-only host for keyboard input and popup lifecycle regression tests. */
class SkinTestHostActivity : Activity() {
    lateinit var keyboard: ViewGroup
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setTheme(R.style.Theme_MarkdownKeyboard)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        val root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(128,128,128)) }
        val type = intent.getStringExtra("keyboard") ?: "kana"
        val id = if (intent.getBooleanExtra("dark",false)) KeyboardSkinId.CUPERTINO_DARK else KeyboardSkinId.CUPERTINO_LIGHT
        keyboard = if(type=="kana") {
            val inflated=LayoutInflater.from(this).inflate(R.layout.main_layout,null)
            inflated.findViewById<TenKey>(R.id.keyboard_view).also { (it.parent as ViewGroup).removeView(it) }
        } else QWERTYKeyboardView(this)
        keyboard.visibility=View.VISIBLE
        val palette = requireNotNull(KeyboardSkinRegistry.find(id)).palette
        keyboard.javaClass.methods.single { it.name=="applyKeyboardTheme" }.invoke(keyboard,
            "custom",android.content.res.Configuration.UI_MODE_NIGHT_NO,false,
            palette.background,palette.key,palette.key,palette.text,palette.text,false,false,Color.BLACK,255,1,id)
        keyboard.javaClass.methods.single { it.name=="setLongPressTimeout" }.invoke(keyboard,300L)
        root.addView(keyboard,FrameLayout.LayoutParams(-1,(280*resources.displayMetrics.density).toInt(),Gravity.BOTTOM))
        root.setOnApplyWindowInsetsListener { view, insets ->
            val bars = androidx.core.view.WindowInsetsCompat.toWindowInsetsCompat(insets, view)
                .getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, 0, bars.right, bars.bottom)
            insets
        }
        setContentView(root)
        root.requestApplyInsets()
    }
}
