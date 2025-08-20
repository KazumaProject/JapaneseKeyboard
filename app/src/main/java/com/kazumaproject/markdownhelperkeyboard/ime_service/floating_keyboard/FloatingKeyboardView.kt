package com.kazumaproject.markdownhelperkeyboard.ime_service.floating_keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.widget.FrameLayout
import com.kazumaproject.core.domain.key.Key
import com.kazumaproject.core.domain.listener.FlickListener
import com.kazumaproject.core.domain.listener.LongPressListener
import com.kazumaproject.core.domain.state.GestureType
import com.kazumaproject.markdownhelperkeyboard.databinding.FloatingKeyboardLayoutBinding
import com.kazumaproject.markdownhelperkeyboard.ime_service.adapters.SuggestionAdapter
import timber.log.Timber

interface FloatingKeyboardListener {
    fun onKey(key: Key, char: Char?, gestureType: GestureType)
    fun onLongPress(key: Key)
}

interface DragListener {
    fun onDrag(x: Int, y: Int)
}

class FloatingKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding: FloatingKeyboardLayoutBinding

    // Listeners to communicate events outwards
    var keyboardListener: FloatingKeyboardListener? = null
    var dragListener: DragListener? = null

    // Properties for tracking drag state
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    init {
        // Inflate the layout and attach it to this custom view
        binding = FloatingKeyboardLayoutBinding.inflate(LayoutInflater.from(context), this, true)
        setupKeyboardListeners()
        setupDragListener()
    }

    // Public method to update suggestions
    fun getSuggestionAdapter(): SuggestionAdapter? { // Replace YourSuggestionAdapter with your actual adapter
        // This allows the parent (service/activity) to interact with the adapter if needed
        return binding.suggestionRecyclerView.adapter as? SuggestionAdapter
    }

    private fun setupKeyboardListeners() {
        binding.keyboardViewFloating.apply {
            setOnFlickListener(object : FlickListener {
                override fun onFlick(gestureType: GestureType, key: Key, char: Char?) {
                    Timber.d("Floating Flick: $char $key $gestureType")
                    // Instead of handling logic here, pass the event to the listener
                    keyboardListener?.onKey(key, char, gestureType)
                }
            })
            setOnLongPressListener(object : LongPressListener {
                override fun onLongPress(key: Key) {
                    Timber.d("Long Press: $key")
                    // Pass the event to the listener
                    keyboardListener?.onLongPress(key)
                }
            })
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDragListener() {
        binding.dragHandle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Record the initial position of the window and touch when dragging starts
                    val location = IntArray(2)
                    // We assume this view is the content of a PopupWindow
                    // The parent will give us the starting location
                    this.getLocationOnScreen(location)
                    initialX = location[0]
                    initialY = location[1]
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val newX = initialX + (event.rawX - initialTouchX)
                    val newY = initialY + (event.rawY - initialTouchY)
                    Timber.d("dragHandle.setOnTouchListener: $newX $newY")
                    // Instead of updating the PopupWindow directly, notify the listener
                    dragListener?.onDrag(newX.toInt(), newY.toInt())
                    true
                }

                else -> false
            }
        }
    }
}
