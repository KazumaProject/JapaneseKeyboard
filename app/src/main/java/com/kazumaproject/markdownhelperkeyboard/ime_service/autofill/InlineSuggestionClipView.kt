/*
 * Copyright 2026 KazumaProject
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.kazumaproject.markdownhelperkeyboard.ime_service.autofill

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.util.AttributeSet
import android.view.Choreographer
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout

/**
 * Clips embedded inline-suggestion surfaces to the visible candidate-strip area.
 *
 * The implementation deliberately avoids a static reference to InlineContentView so this layout
 * class can still be inflated on Android versions earlier than API 30.
 */
class InlineSuggestionClipView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val parentBounds = Rect()
    private val contentBounds = Rect()
    private val backgroundView = SurfaceView(context).apply {
        setZOrderOnTop(true)
        holder.setFormat(PixelFormat.TRANSPARENT)
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                drawBackgroundColorIfReady()
            }

            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int,
            ) = Unit

            override fun surfaceDestroyed(holder: SurfaceHolder) = Unit
        })
    }
    private var surfaceBackgroundColor = Color.TRANSPARENT
    private val onDrawListener = ViewTreeObserver.OnDrawListener {
        clipInlineContentDescendants(this)
    }

    init {
        // A SurfaceView background keeps the remote suggestion surfaces composited with the IME
        // strip. This follows AOSP's AutofillKeyboard InlineContentClipView implementation.
        addView(backgroundView)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnDrawListener(onDrawListener)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (viewTreeObserver.isAlive) {
            viewTreeObserver.removeOnDrawListener(onDrawListener)
        }
    }

    override fun setBackgroundColor(color: Int) {
        surfaceBackgroundColor = color
        Choreographer.getInstance().postFrameCallback {
            drawBackgroundColorIfReady()
        }
    }

    private fun drawBackgroundColorIfReady() {
        val surface: Surface = backgroundView.holder.surface
        if (!surface.isValid) return
        val canvas: Canvas = surface.lockCanvas(null)
        try {
            canvas.drawColor(surfaceBackgroundColor)
        } finally {
            surface.unlockCanvasAndPost(canvas)
        }
    }

    private fun clipInlineContentDescendants(root: View?) {
        if (root == null || width <= 0 || height <= 0) return
        if (root.javaClass.name == INLINE_CONTENT_VIEW_CLASS_NAME) {
            parentBounds.set(0, 0, width, height)
            contentBounds.set(parentBounds)
            offsetRectIntoDescendantCoords(root, contentBounds)
            root.clipBounds = contentBounds
            return
        }
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                clipInlineContentDescendants(root.getChildAt(index))
            }
        }
    }

    private companion object {
        const val INLINE_CONTENT_VIEW_CLASS_NAME = "android.widget.inline.InlineContentView"
    }
}
