
import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatImageButton
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.ime_service.components.InputModeSwitch

class KeyboardView @JvmOverloads constructor(
    context: Context, private val attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val keyReturn: AppCompatImageButton
    private val keySoftLeft: AppCompatImageButton
    private val keyLanguageSwitch: AppCompatButton
    private val keySwitchKeyMode: InputModeSwitch
    private val key1: AppCompatButton
    private val key2: AppCompatButton
    private val key3: AppCompatButton
    private val key4: AppCompatButton
    private val key5: AppCompatButton
    private val key6: AppCompatButton
    private val key7: AppCompatButton
    private val key8: AppCompatButton
    private val key9: AppCompatButton
    private val keySmallLetter: AppCompatImageButton
    private val key11: AppCompatButton
    private val key12: AppCompatButton
    private val keyDelete: AppCompatImageButton
    private val keyMoveCursorRight: AppCompatImageButton
    private val keySpace: AppCompatImageButton
    private val keyEnter: AppCompatImageButton

    init {
        // Set up the ConstraintLayout
        layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            resources.getDimensionPixelSize(R.dimen.keyboard_height)
        )
        setBackgroundColor(ContextCompat.getColor(context, R.color.keyboard_bg))

        keyReturn = createImageButton(R.id.key_return, R.drawable.baseline_turn_left_24, R.string.char_previous)
        keySoftLeft = createImageButton(R.id.key_soft_left, R.drawable.baseline_arrow_left_24, R.string.left_key)
        keyLanguageSwitch = createButton(R.id.key_language_switch, R.drawable.baseline_emoji_emotions_24,)
        keySwitchKeyMode = createInputModeSwitch(R.id.key_switch_key_mode, R.string.key_switch)

        key1 = createButton(R.id.key_1, R.string.string_あ)
        key2 = createButton(R.id.key_2, R.string.string_か)
        key3 = createButton(R.id.key_3, R.string.string_さ)
        key4 = createButton(R.id.key_4, R.string.string_た)
        key5 = createButton(R.id.key_5, R.string.string_な)
        key6 = createButton(R.id.key_6, R.string.string_は)
        key7 = createButton(R.id.key_7, R.string.string_ま)
        key8 = createButton(R.id.key_8, R.string.string_や)
        key9 = createButton(R.id.key_9, R.string.string_ら)

        keySmallLetter = createImageButton(R.id.key_small_letter, R.drawable.kana_small, R.string.small_key)
        key11 = createButton(R.id.key_11, R.string.string_わ)
        key12 = createButton(R.id.key_12, R.string.string_ten_hatena)
        keyDelete = createImageButton(R.id.key_delete, R.drawable.baseline_backspace_24, R.string.delete_key)
        keyMoveCursorRight = createImageButton(R.id.key_move_cursor_right, R.drawable.baseline_arrow_right_24, R.string.key_right)
        keySpace = createImageButton(R.id.key_space, R.drawable.baseline_space_bar_24, R.string.space_key)
        keyEnter = createImageButton(R.id.key_enter, R.drawable.baseline_arrow_right_alt_24, R.string.enter_key)

        // Add views to the ConstraintLayout
        addViews()

        // Apply constraints
        applyConstraints()
    }

    private fun createImageButton(id: Int, drawableRes: Int, contentDescriptionRes: Int): AppCompatImageButton {
        return AppCompatImageButton(context).apply {
            this.id = id
            setImageResource(drawableRes)
            contentDescription = context.getString(contentDescriptionRes)
            //scaleType = ImageButton.ScaleType.CENTER
            background = ContextCompat.getDrawable(context, R.drawable.ten_keys_side_bg)
            setPaddingRelative(12, 12, 12, 12)
            layoutParams = LayoutParams(0, 0).apply {
                setMargins(
                    resources.getDimensionPixelSize(R.dimen.key_margin_start_size),
                    resources.getDimensionPixelSize(R.dimen.key_margin_vertical_size),
                    resources.getDimensionPixelSize(R.dimen.key_margin_horizontal_size),
                    resources.getDimensionPixelSize(R.dimen.key_margin_vertical_size)
                )
            }
        }
    }

    private fun createButton(id: Int, textRes: Int): AppCompatButton {
        return AppCompatButton(context).apply {
            this.id = id
            text = context.getString(textRes)
            setTextColor(ContextCompat.getColor(context, R.color.keyboard_icon_color))
            textSize = resources.getDimension(R.dimen.key_text_size)
            letterSpacing = 0.1f
            background = ContextCompat.getDrawable(context, R.drawable.ten_keys_center_bg)
            layoutParams = LayoutParams(0, 0).apply {
                setMargins(
                    resources.getDimensionPixelSize(R.dimen.key_margin_horizontal_size),
                    resources.getDimensionPixelSize(R.dimen.key_margin_vertical_size),
                    resources.getDimensionPixelSize(R.dimen.key_margin_horizontal_size),
                    resources.getDimensionPixelSize(R.dimen.key_margin_vertical_size)
                )
            }
        }
    }

    private fun createInputModeSwitch(id: Int, contentDescriptionRes: Int): InputModeSwitch {
        return InputModeSwitch(context, attrs!!).apply {
            this.id = id
            contentDescription = context.getString(contentDescriptionRes)
            background = ContextCompat.getDrawable(context, R.drawable.ten_keys_side_bg)
            layoutParams = LayoutParams(0, 0).apply {
                setMargins(
                    resources.getDimensionPixelSize(R.dimen.key_margin_start_size),
                    resources.getDimensionPixelSize(R.dimen.key_margin_vertical_size),
                    resources.getDimensionPixelSize(R.dimen.key_margin_horizontal_size),
                    resources.getDimensionPixelSize(R.dimen.key_margin_vertical_size)
                )
            }
        }
    }

    private fun addViews() {
        addView(keyReturn)
        addView(keySoftLeft)
        addView(keyLanguageSwitch)
        addView(keySwitchKeyMode)
        addView(key1)
        addView(key2)
        addView(key3)
        addView(key4)
        addView(key5)
        addView(key6)
        addView(key7)
        addView(key8)
        addView(key9)
        addView(keySmallLetter)
        addView(key11)
        addView(key12)
        addView(keyDelete)
        addView(keyMoveCursorRight)
        addView(keySpace)
        addView(keyEnter)
    }

    private fun applyConstraints() {
        val set = ConstraintSet()
        set.clone(this)

        // Apply constraints for each view
        // For brevity, this example shows constraints for one view; you need to add constraints for all views as per the XML file

        set.connect(keyReturn.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
        set.connect(keyReturn.id, ConstraintSet.END, key1.id, ConstraintSet.START)
        set.connect(keyReturn.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
        set.connect(keyReturn.id, ConstraintSet.BOTTOM, keySoftLeft.id, ConstraintSet.TOP)

        set.connect(keySoftLeft.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
        set.connect(keySoftLeft.id, ConstraintSet.END, key4.id, ConstraintSet.START)
        set.connect(keySoftLeft.id, ConstraintSet.TOP, keyReturn.id, ConstraintSet.BOTTOM)
        set.connect(keySoftLeft.id, ConstraintSet.BOTTOM, keyLanguageSwitch.id, ConstraintSet.TOP)

        set.connect(keyLanguageSwitch.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
        set.connect(keyLanguageSwitch.id, ConstraintSet.END, key7.id, ConstraintSet.START)
        set.connect(keyLanguageSwitch.id, ConstraintSet.TOP, keySoftLeft.id, ConstraintSet.BOTTOM)
        set.connect(keyLanguageSwitch.id, ConstraintSet.BOTTOM, keySwitchKeyMode.id, ConstraintSet.TOP)

        set.connect(keySwitchKeyMode.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
        set.connect(keySwitchKeyMode.id, ConstraintSet.END, keySmallLetter.id, ConstraintSet.START)
        set.connect(keySwitchKeyMode.id, ConstraintSet.TOP, keyLanguageSwitch.id, ConstraintSet.BOTTOM)
        set.connect(keySwitchKeyMode.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)

        // Apply remaining constraints for other views...

        set.applyTo(this)
    }
}