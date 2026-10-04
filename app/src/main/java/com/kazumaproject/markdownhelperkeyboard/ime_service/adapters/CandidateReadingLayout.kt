package com.kazumaproject.markdownhelperkeyboard.ime_service.adapters

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.text.TextUtils
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.textview.MaterialTextView
import com.kazumaproject.markdownhelperkeyboard.R
import kotlin.math.ceil

/** A ruby annotation contributes width, but never moves the candidate's body. */
class CandidateReadingTextView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : MaterialTextView(context, attrs) {
    internal var leadingInsetPx = 0f
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
            }
        }
    internal val inkBounds = Rect()
    internal var inkScale = 1f

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Keep TextView's Layout so text and font changes retain its remeasurement behavior.
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val value = text.toString()
        paint.getTextBounds(value, 0, value.length, inkBounds)
        val width = ceil(leadingInsetPx + maxOf(paint.measureText(value), inkBounds.right.toFloat())).toInt()
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), 0)
    }

    override fun onDraw(canvas: Canvas) {
        paint.color = currentTextColor
        paint.drawableState = drawableState
        canvas.save()
        canvas.clipRect(0, 0, width, height)
        canvas.translate(leadingInsetPx, 0f)
        canvas.scale(inkScale, inkScale)
        val visibleText = if (inkScale > 0f) {
            TextUtils.ellipsize(text, paint,
                ((width - leadingInsetPx) / inkScale).coerceAtLeast(0f), TextUtils.TruncateAt.END)
        } else ""
        canvas.drawText(visibleText.toString(), 0f, -inkBounds.top.toFloat(), paint)
        canvas.restore()
    }
}

/** Layout the annotation against the body's ink, independently of font leading. */
class CandidateReadingLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : ConstraintLayout(context, attrs) {
    private val bodyBounds = Rect()
    private val parentBounds = Rect()
    private val bodyInk = Rect()

    private fun annotationHeadroom(): Int {
        val recycler = parent as? RecyclerView ?: return 0
        val manager = recycler.layoutManager as? LinearLayoutManager ?: return 0
        if (manager.orientation != RecyclerView.HORIZONTAL) return 0
        var frame = recycler.parent as? ViewGroup ?: return 0
        var recyclerTop = recycler.top
        // Previews have a wrap-content area inside their fixed-height frame.
        // Include that area's offset while retaining the body's original position.
        while (frame.layoutParams?.height == ViewGroup.LayoutParams.WRAP_CONTENT) {
            val outer = frame.parent as? ViewGroup ?: break
            frame.clipChildren = false
            recyclerTop += frame.top
            frame = outer
        }
        // Both ancestors must allow drawing above the centered RecyclerView. Layout
        // managers can be installed after attachment, so update this during layout.
        recycler.clipChildren = false
        frame.clipChildren = false
        // Keep horizontal scrolling clipped before the expand button; only the
        // vertical viewport grows into the existing candidate frame.
        recycler.clipBounds = Rect(0, frame.paddingTop - recyclerTop,
            recycler.width, frame.height - frame.paddingBottom - recyclerTop)
        return (top + recyclerTop - frame.paddingTop).coerceAtLeast(0)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val body = findViewById<TextView>(R.id.suggestion_item_text_view)
        val reading = findViewById<CandidateReadingTextView>(R.id.suggestion_item_yomi_text_view)
        val value = body.text.toString()
        reading.leadingInsetPx = body.paint.measureText(value.takeWhile(Char::isWhitespace))
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        val body = findViewById<TextView>(R.id.suggestion_item_text_view)
        val reading = findViewById<CandidateReadingTextView>(R.id.suggestion_item_yomi_text_view)
        if (reading.visibility != View.VISIBLE || body.visibility != View.VISIBLE) return
        val value = body.text.toString()
        body.paint.getTextBounds(value, 0, value.length, bodyInk)
        bodyBounds.set(0, 0, body.width, body.height)
        offsetDescendantRectToMyCoords(body, bodyBounds)
        val parent = reading.parent as ViewGroup
        parentBounds.set(0, 0, parent.width, parent.height)
        offsetDescendantRectToMyCoords(parent, parentBounds)
        val gap = ceil(resources.displayMetrics.density.toDouble()).toInt()
        val annotationLeft = bodyBounds.left + body.compoundPaddingLeft - parentBounds.left
        val inkTop = bodyBounds.top + body.baseline + bodyInk.top
        // Padding belongs to the body; the independent annotation may use that space.
        val availableHeight = (inkTop + annotationHeadroom() - gap).coerceAtLeast(0)
        val annotationHeight = reading.inkBounds.height().coerceAtMost(availableHeight)
        reading.inkScale = if (reading.inkBounds.height() > 0) {
            annotationHeight.toFloat() / reading.inkBounds.height()
        } else 1f
        val annotationTop = inkTop - gap - annotationHeight - parentBounds.top
        reading.layout(annotationLeft, annotationTop,
            annotationLeft + reading.measuredWidth, annotationTop + annotationHeight)
    }
}
