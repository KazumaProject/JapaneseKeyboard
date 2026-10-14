package com.kazumaproject.markdownhelperkeyboard.ime_service.fever

import android.content.Context
import android.widget.FrameLayout
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FeverControllerTest {
    @Test fun editingCuesDoNotCountDrawOrExtendTheComboDeadline() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().putInt(FeverSettings.HIT_RATE, 100).commit()
        val host = FrameLayout(context).apply { layout(0, 0, 500, 400) }
        val controller = FeverController(context) { Triple(false, false, 0) }
        val events = mutableListOf<FeverEvent>()
        controller.eventObserver = { events.add(it) }
        controller.configure(true, host, host, null)
        for (action in listOf(android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_MOVE, android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL)) {
            val touch = android.view.MotionEvent.obtain(100, 100, action, 25f, 40f, 0)
            controller.touch(touch, true); controller.touch(touch, false); touch.recycle()
        }
        assertEquals(0, controller.combo); assertTrue(events.isEmpty())
        controller.begin(); controller.noteCharacter(); controller.end()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(2, java.util.concurrent.TimeUnit.SECONDS)
        FeverCue.entries.forEach { cue ->
            controller.begin(); controller.noteCue(cue); controller.noteCue(cue); controller.end()
            assertEquals(cue, events.last().cue)
            assertNull(events.last().award)
            assertEquals(1, controller.combo)
        }
        assertEquals(1 + FeverCue.entries.size, events.size)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(1, java.util.concurrent.TimeUnit.SECONDS)
        assertEquals(0, controller.combo)
        controller.stop(); val before = events.size
        controller.noteCue(FeverCue.DELETE)
        assertEquals(before, events.size)
        controller.release()
    }

    @Test fun candidateRevealIsDeduplicatedCancelledAndDoesNotCountInput() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().putInt(FeverSettings.HIT_RATE, 100).commit()
        val host = FrameLayout(context).apply { layout(0, 0, 500, 400) }
        val controller = FeverController(context) { Triple(false, false, 0) }
        val events = mutableListOf<FeverEvent>()
        controller.eventObserver = events::add
        controller.configure(true, host, host, null)
        val main = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
        controller.candidatesShown(true); controller.candidatesShown(true)
        main.idleFor(40, java.util.concurrent.TimeUnit.MILLISECONDS)
        assertEquals(listOf(FeverEvent(0, cue = FeverCue.CANDIDATES)), events)
        assertEquals(0, controller.combo)
        controller.candidatesShown(true)
        main.idleFor(40, java.util.concurrent.TimeUnit.MILLISECONDS)
        assertEquals(1, events.size)
        controller.candidatesShown(false); controller.candidatesShown(true); controller.candidatesShown(false)
        main.idleFor(40, java.util.concurrent.TimeUnit.MILLISECONDS)
        assertEquals(1, events.size)
        controller.candidatesShown(true); controller.stop()
        main.idleFor(40, java.util.concurrent.TimeUnit.MILLISECONDS)
        assertEquals(1, events.size)
        controller.release()
    }

    @Test fun nestedCharacterCallbacksCountOnceWhileCandidateUpdatesAndPasteDoNot() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().putInt(FeverSettings.HIT_RATE, 0).commit()
        val host = FrameLayout(context).apply { layout(0, 0, 500, 400) }
        val controller = FeverController(context) { Triple(false, false, 0) }
        val events = mutableListOf<FeverEvent>()
        controller.eventObserver = { events.add(it) }
        controller.configure(true, host, host, null)
        controller.begin(); controller.noteCharacter(); controller.begin(); controller.noteCharacter(); controller.end(); controller.end()
        assertEquals(1, controller.combo); assertEquals(1, events.size)
        controller.begin(); controller.committed("候補", true); controller.end()
        assertEquals(1, controller.combo)
        controller.begin(); controller.suppressCommit(); controller.committed("最高", true); controller.end()
        assertEquals(1, events.size)
        controller.begin(); controller.noteCharacter(); controller.committed("失敗", false); controller.end()
        assertEquals(1, controller.combo)
        controller.begin(); controller.committed("最高", true); controller.end()
        assertEquals(FeverAward.HOT, events.last().award)
        events.clear()
        controller.composing("finished composition", true)
        controller.begin(); controller.finished(true); controller.end()
        assertTrue("Cursor/cleanup finishes do not draw", events.isEmpty())
        controller.composing("好き", true)
        controller.begin(); controller.noteConfirmation(); controller.finished(false); controller.end()
        assertTrue(events.isEmpty())
        controller.begin(); controller.noteConfirmation(); controller.finished(true); controller.end()
        // The prior HOT award is still on screen; resetting also proves that no text survives a session.
        controller.stop(); assertEquals(0, controller.combo)
        controller.begin(); controller.noteCharacter(); controller.committed("最高", true); controller.end()
        assertEquals(0, controller.combo)
        controller.release()
    }
}
