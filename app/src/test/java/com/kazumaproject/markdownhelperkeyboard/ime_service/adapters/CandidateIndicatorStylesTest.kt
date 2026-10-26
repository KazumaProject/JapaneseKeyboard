package com.kazumaproject.markdownhelperkeyboard.ime_service.adapters

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.CANDIDATE_TYPE_USER_DICTIONARY
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.ime_service.ImePreferencesSnapshot
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CandidateIndicatorStylesTest {
    private val context get() = android.view.ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(), R.style.Theme_MarkdownKeyboard)

    @Test fun stylesPersistIndependentlySurviveSnapshotsAndResetWithoutChangingSwitch() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().clear().putBoolean(AppPreference.CANDIDATE_CHARACTER_INDICATORS_KEY, true).commit()
        CandidateIndicatorGroup.entries.forEachIndexed { i, group ->
            prefs.edit().putString(CandidateIndicatorSettings.lineKey(group), CandidateIndicatorLineStyle.entries[i + 1].value)
                .putInt(CandidateIndicatorSettings.colorKey(group, false), Color.rgb(20 + i, 70, 180))
                .putInt(CandidateIndicatorSettings.colorKey(group, true), Color.rgb(180, 70, 20 + i)).commit()
        }
        val styles = CandidateIndicatorSettings.read(prefs)
        AppPreference.init(context)
        assertEquals(styles, ImePreferencesSnapshot.from(AppPreference).appearance.candidateIndicatorStyles)
        CandidateIndicatorGroup.entries.forEachIndexed { i, group ->
            assertEquals(CandidateIndicatorLineStyle.entries[i + 1], styles[group].line)
            assertEquals(Color.rgb(20 + i, 70, 180), styles[group].color(context, group, false))
            assertEquals(Color.rgb(180, 70, 20 + i), styles[group].color(context, group, true))
        }
        CandidateIndicatorSettings.reset(prefs)
        assertEquals(CandidateIndicatorStyles(), CandidateIndicatorSettings.read(prefs))
        assertTrue(prefs.getBoolean(AppPreference.CANDIDATE_CHARACTER_INDICATORS_KEY, false))
    }

    @Test fun invalidBackupValuesFallBackAndDefaultColorsFollowTheme() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().clear().putString(CandidateIndicatorSettings.lineKey(CandidateIndicatorGroup.KATAKANA), "invalid")
            .putString(CandidateIndicatorSettings.colorKey(CandidateIndicatorGroup.KATAKANA, false), "invalid").commit()
        val styles = CandidateIndicatorSettings.read(prefs)
        assertEquals(CandidateIndicatorStyles(), styles)
        prefs.edit().putInt(CandidateIndicatorSettings.lineKey(CandidateIndicatorGroup.FULL_WIDTH), 123).commit()
        CandidateIndicatorSettings.normalize(prefs)
        assertFalse(prefs.contains(CandidateIndicatorSettings.lineKey(CandidateIndicatorGroup.FULL_WIDTH)))
        assertFalse(prefs.contains(CandidateIndicatorSettings.colorKey(CandidateIndicatorGroup.KATAKANA, false)))
        for (group in CandidateIndicatorGroup.entries) {
            assertNotEquals(styles[group].color(context, group, false), styles[group].color(context, group, true))
        }
    }

    private fun pixels(view: View): IntArray {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return IntArray(view.width * view.height).also {
            bitmap.getPixels(it, 0, view.width, 0, 0, view.width, view.height)
            bitmap.recycle()
        }
    }
    private fun measure(view: View) {
        val spec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        view.measure(spec, spec)
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    @Test fun allLineStylesRenderAtEachSizeWithoutChangingGeometryAndClearOnReuse() {
        for (size in listOf(8f, 14f, 28f)) {
            val view = CandidateCharacterTextView(context).apply {
                text = "カタカナカタカナ"
                textSize = size
                setTextColor(Color.BLACK)
            }
            measure(view)
            val baselinePixels = pixels(view)
            val dimensions = listOf(view.width, view.height, view.baseline)
            val variants = mutableMapOf<CandidateIndicatorLineStyle, List<Int>>()
            for (line in CandidateIndicatorLineStyle.entries) {
                val style = CandidateIndicatorStyle(line, Color.RED, Color.GREEN)
                view.setCharacterIndicators(view.text.toString(), true, styles = CandidateIndicatorStyles(katakana = style), dark = false)
                measure(view)
                assertEquals(dimensions, listOf(view.width, view.height, view.baseline))
                val drawn = pixels(view)
                assertEquals(line != CandidateIndicatorLineStyle.NONE, Color.RED in drawn)
                if (line == CandidateIndicatorLineStyle.NONE) assertArrayEquals(baselinePixels, drawn)
                else variants[line] = drawn.toList()
                view.setCharacterIndicators(view.text.toString(), true, styles = CandidateIndicatorStyles(katakana = style), dark = true)
                assertEquals(line != CandidateIndicatorLineStyle.NONE, Color.GREEN in pixels(view))
            }
            assertEquals(4, variants.values.toSet().size)
            view.setCharacterIndicators("ひらがな", false)
            assertNull(view.contentDescription)
            assertArrayEquals(baselinePixels, pixels(view))
        }
    }

    @Test fun halfKanaHasIndependentStyleAndSelectedLinesUseTextColor() {
        val view = CandidateCharacterTextView(context).apply { text = "ABCｶﾅ"; textSize = 24f; setTextColor(Color.BLACK) }
        measure(view)
        val styles = CandidateIndicatorStyles(
            halfWidth = CandidateIndicatorStyle(CandidateIndicatorLineStyle.NONE),
            halfKatakana = CandidateIndicatorStyle(CandidateIndicatorLineStyle.DASHED, Color.MAGENTA, Color.CYAN),
        )
        view.setCharacterIndicators(view.text.toString(), true, styles = styles, dark = false)
        assertTrue(Color.MAGENTA in pixels(view))
        view.setCharacterIndicators(view.text.toString(), true, selected = true, styles = styles, dark = false)
        assertFalse(Color.MAGENTA in pixels(view))
        view.setCharacterIndicators(view.text.toString(), true, styles = styles, dark = true)
        assertTrue(Color.CYAN in pixels(view))
    }

    @Test fun sourceCandidateLabelsAndMeasuredRowSizeDoNotGrowWhenIndicatorsAreEnabled() {
        val adapter = SuggestionAdapter()
        adapter.setShowDictionaryCandidateLabels(true)
        adapter.suggestions = listOf(Candidate("東京でカフェ１２３ABC", CANDIDATE_TYPE_USER_DICTIONARY, 3u, 0))
        for (attempt in 0 until 100) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            if (adapter.itemCount == 1) break
            Thread.sleep(10)
        }
        assertEquals(1, adapter.itemCount)
        val holder = adapter.onCreateViewHolder(android.widget.FrameLayout(context), adapter.getItemViewType(0))
        val sizes = mutableListOf<Pair<Int, Int>>()
        for (enabled in listOf(false, true, false)) {
            adapter.setShowCharacterIndicators(enabled)
            adapter.onBindViewHolder(holder, 0)
            measure(holder.itemView)
            sizes += holder.itemView.measuredWidth to holder.itemView.measuredHeight
            assertEquals(candidateLabelPresentation(context, adapter.suggestions.single(), true, false).toString(), holder.itemView.findViewById<android.widget.TextView>(R.id.suggestion_item_type_text_view).text.toString())
        }
        assertEquals(1, sizes.distinct().size)
        adapter.release()
    }
}
