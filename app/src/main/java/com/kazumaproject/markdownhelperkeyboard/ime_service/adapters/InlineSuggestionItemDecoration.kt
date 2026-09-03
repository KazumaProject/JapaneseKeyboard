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
package com.kazumaproject.markdownhelperkeyboard.ime_service.adapters

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/** Applies the only spacing used by the inline suggestion strip. */
internal class InlineSuggestionItemDecoration(
    private val edgeSpacing: Int,
    private val itemSpacing: Int,
) : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State,
    ) {
        val position = parent.getChildAdapterPosition(view)
        outRect.set(offsetsForPosition(position, state.itemCount))
    }

    internal fun offsetsForPosition(position: Int, itemCount: Int): Rect {
        if (position == RecyclerView.NO_POSITION || itemCount <= 0) {
            return Rect()
        }
        return Rect(
            if (position == 0) edgeSpacing else itemSpacing,
            0,
            if (position == itemCount - 1) edgeSpacing else 0,
            0,
        )
    }
}
