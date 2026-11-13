package com.kazumaproject.markdownhelperkeyboard

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FastInputHostSessionDeviceTest {
    @Test
    fun launchRecreateAndCloseTrackOnlyThisHost() {
        val session = FastInputHostSession.launch(
            InstrumentationRegistry.getInstrumentation().targetContext
        )
        lateinit var first: FastInputHostActivity
        lateinit var second: FastInputHostActivity
        try {
            session.onActivity { first = it }
            session.recreate()
            session.onActivity { second = it }
            assertNotSame(first, second)
            assertTrue(first.isDestroyed)
        } finally {
            session.close()
        }
        assertTrue(second.isDestroyed)
        session.close()
    }
}
