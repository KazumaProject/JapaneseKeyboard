package com.kazumaproject.markdownhelperkeyboard.gemma

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AiModelProfilesTest {
    @Test fun projectorAssociationsRemainIndependentAndCanBeRemoved() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("ai-profiles-test", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        val profiles = AiModelProfiles(preferences)
        profiles.setProjectorPath("/models/vision.gguf", "/projectors/vision.gguf")
        profiles.setProjectorPath("/models/audio.gguf", "/projectors/audio.gguf")
        val restored = AiModelProfiles(preferences)
        assertEquals("/projectors/vision.gguf", restored.projectorPath("/models/vision.gguf"))
        assertEquals("/projectors/audio.gguf", restored.projectorPath("/models/audio.gguf"))
        restored.setProjectorPath("/models/vision.gguf", "")
        assertEquals("", restored.projectorPath("/models/vision.gguf"))
        assertEquals("/projectors/audio.gguf", restored.projectorPath("/models/audio.gguf"))
    }

    @Test fun textInstructionContainsNoModelSpecificRoleTokens() {
        val prompt = GemmaPromptBuilder.buildInstruction("Translate into English.")
        assertTrue(prompt.contains("Translate into English."))
        assertFalse(prompt.contains("Gemma", true))
        assertFalse(prompt.contains("<start_of_turn>"))
    }
}
