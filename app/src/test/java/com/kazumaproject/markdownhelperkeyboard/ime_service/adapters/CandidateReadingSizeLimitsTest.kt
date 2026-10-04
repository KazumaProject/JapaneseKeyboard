package com.kazumaproject.markdownhelperkeyboard.ime_service.adapters

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.Typeface
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.core.ui.font.KeyboardFontApplicator
import com.kazumaproject.core.ui.font.KeyboardFontSnapshot
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CandidateReadingSizeLimitsTest {
    private val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(), R.style.Theme_MarkdownKeyboard)

    @Before fun setUp() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        AppPreference.init(context)
        KeyboardFontApplicator.updateProcessSnapshot(KeyboardFontSnapshot())
    }

    @org.junit.After fun restoreFont() {
        KeyboardFontApplicator.updateProcessSnapshot(KeyboardFontSnapshot())
    }

    @Test fun selectableSizesProduceDifferentInkInsteadOfSaturatingAtNormalRowHeights() {
        for (rows in 1..3) {
            AppPreference.setCandidateColumnAndSyncHeight(false, rows.toString())
            AppPreference.setCandidateColumnAndSyncHeight(true, rows.toString())
            val maximum = CandidateReadingSizeLimits.maximumSp(context)
            assertTrue("there must be multiple drawable sizes", maximum > 1)
            assertTrue("the former 24sp upper bound must be reduced", maximum < 24)
            val root = LayoutInflater.from(context).inflate(R.layout.suggestion_item, null) as CandidateReadingLayout
            val body = root.findViewById<TextView>(R.id.suggestion_item_text_view)
            val reading = root.findViewById<CandidateReadingTextView>(R.id.suggestion_item_yomi_text_view)
            body.text = "    感じ    "
            reading.text = "かんじ"
            reading.visibility = View.VISIBLE
            val frame = FrameLayout(context)
            val recycler = RecyclerView(context).apply {
                layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
                clipChildren = false
                clipToPadding = false
            }
            frame.addView(recycler)
            val frameHeight = (AppPreference.getCandidateVisibleHeightDp(false, rows.toString()) * context.resources.displayMetrics.density).toInt()
            val spacing = if (rows > 1) context.resources.getDimensionPixelSize(com.kazumaproject.core.R.dimen.grid_spacing) else 0
            val itemHeight = minOf((36 * context.resources.displayMetrics.density).toInt(), frameHeight / rows - spacing - spacing / rows)
            val stripHeight = rows * itemHeight + if (rows > 1) (rows + 1) * spacing else 0
            frame.layout(0, 0, 1000, frameHeight)
            recycler.layout(0, (frameHeight - stripHeight) / 2, 1000, (frameHeight + stripHeight) / 2)
            recycler.addView(root)
            var before: Rect? = null
            val heights = mutableListOf<Int>()
            for (size in listOf(1, maximum)) {
                reading.textSize = size.toFloat()
                val height = itemHeight
                root.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.AT_MOST))
                root.layout(0, spacing, root.measuredWidth, spacing + root.measuredHeight)
                assertEquals("selected font size must draw without shrinking", 1f, reading.inkScale, .001f)
                val rect = Rect(0, 0, body.width, body.height)
                root.offsetDescendantRectToMyCoords(body, rect)
                before?.let { assertEquals(it, rect) }
                before = rect
                val image = Bitmap.createBitmap(frame.width, frame.height, Bitmap.Config.ARGB_8888)
                frame.draw(Canvas(image))
                val annotation = Rect(0, 0, reading.width, reading.height)
                frame.offsetDescendantRectToMyCoords(reading, annotation)
                assertTrue("reading stays inside fixed frame", annotation.top >= 0)
                reading.visibility = View.GONE
                val without = Bitmap.createBitmap(frame.width, frame.height, Bitmap.Config.ARGB_8888)
                frame.draw(Canvas(without))
                val inkRows = (annotation.top until annotation.bottom).count { y ->
                    (annotation.left until annotation.right).any { x -> image.getPixel(x, y) != without.getPixel(x, y) }
                }
                assertTrue("the reading must actually draw", inkRows > 0)
                heights += inkRows
                reading.visibility = View.VISIBLE
            }
            assertTrue("1sp and ${maximum}sp must have distinct drawing heights", heights.last() > heights.first())
        }
    }

    @Test fun limitRecomputesWhenFontOrCandidateRowsChange() {
        val initial = CandidateReadingSizeLimits.maximumSp(context)
        assertEquals(initial.toFloat(), CandidateReadingSizeLimits.clamp(context, 24f), 0f)
        KeyboardFontApplicator.updateProcessSnapshot(KeyboardFontSnapshot(Typeface.SERIF, 1L))
        val custom = CandidateReadingSizeLimits.maximumSp(context)
        assertTrue(custom in 1..24)
        assertEquals(custom.toFloat(), CandidateReadingSizeLimits.clamp(context, 24f), 0f)
        AppPreference.candidate_letter_size = 24f
        val resized = CandidateReadingSizeLimits.maximumSp(context)
        assertTrue(resized in 1..24)
        assertEquals(1f, CandidateReadingSizeLimits.clamp(context, 1f), 0f)
    }
}
