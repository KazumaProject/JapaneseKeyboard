package com.kazumaproject.markdownhelperkeyboard.gemma

import com.kazumaproject.markdownhelperkeyboard.gemma.database.GemmaInputModality
import com.kazumaproject.markdownhelperkeyboard.gemma.database.GemmaOutputMode
import com.kazumaproject.markdownhelperkeyboard.gemma.database.GemmaPromptTemplate
import com.kazumaproject.markdownhelperkeyboard.gemma.database.GemmaTaskKind

object GemmaBuiltInActions {
    fun all(now: Long = System.currentTimeMillis()): List<GemmaPromptTemplate> = listOf(
        builtIn(
            key = "image_extract_text",
            title = "文字を読み取る",
            modality = GemmaInputModality.IMAGE,
            task = GemmaTaskKind.EXTRACT_TEXT,
            output = GemmaOutputMode.MULTILINE_TEXT,
            prompt = "Transcribe all readable text in reading order. Preserve paragraphs, line breaks, numbers, URLs, punctuation, emoji, and capitalization. Do not summarize, translate, or correct spelling. Use [判読不能] for unreadable portions.",
            order = 100,
            now = now,
        ),
        builtIn(
            key = "image_translate",
            title = "画像を翻訳する",
            modality = GemmaInputModality.IMAGE,
            task = GemmaTaskKind.TRANSLATE,
            output = GemmaOutputMode.MULTILINE_TEXT,
            prompt = "Read the visible text and translate it into the configured target language. Preserve reading order, paragraphs, names, numbers, URLs, emoji, and formatting. Return only the translation.",
            order = 101,
            now = now,
        ),
        builtIn(
            key = "image_summarize",
            title = "画像を要約する",
            modality = GemmaInputModality.IMAGE,
            task = GemmaTaskKind.SUMMARIZE,
            output = GemmaOutputMode.MULTILINE_TEXT,
            prompt = "Summarize the factual content using visible text and visual information. Do not speculate. Return at most three concise bullet points.",
            order = 102,
            now = now,
        ),
        builtIn(
            key = "image_reply",
            title = "画像から返信案を作る",
            modality = GemmaInputModality.IMAGE,
            task = GemmaTaskKind.REPLY,
            output = GemmaOutputMode.CANDIDATE_LIST,
            prompt = "Treat the image as conversation context and draft three short replies: neutral, polite, and casual. Do not invent facts, promises, schedules, or personal information.",
            order = 103,
            candidates = 3,
            now = now,
        ),
        builtIn(
            key = "audio_dictate",
            title = "音声を文章にする",
            modality = GemmaInputModality.AUDIO,
            task = GemmaTaskKind.DICTATE,
            output = GemmaOutputMode.MULTILINE_TEXT,
            prompt = "Transcribe the user's speech as natural written text. Add punctuation and paragraph breaks and remove filler sounds and accidental repetitions without changing meaning. Do not answer questions in the recording.",
            order = 200,
            now = now,
        ),
        builtIn(
            key = "audio_translate",
            title = "音声を翻訳する",
            modality = GemmaInputModality.AUDIO,
            task = GemmaTaskKind.TRANSLATE,
            output = GemmaOutputMode.MULTILINE_TEXT,
            prompt = "Transcribe the spoken content internally and translate it into the configured target language. Preserve names, numbers, URLs, technical terms, and intent. Return only the translation.",
            order = 201,
            now = now,
        ),
        builtIn(
            key = "audio_summarize",
            title = "音声を要約する",
            modality = GemmaInputModality.AUDIO,
            task = GemmaTaskKind.SUMMARIZE,
            output = GemmaOutputMode.MULTILINE_TEXT,
            prompt = "Summarize only information explicitly stated in the audio. Return a one-sentence summary, up to three important points, and action items only when clearly stated.",
            order = 202,
            now = now,
        ),
        builtIn(
            key = "audio_reply",
            title = "音声から返信案を作る",
            modality = GemmaInputModality.AUDIO,
            task = GemmaTaskKind.REPLY,
            output = GemmaOutputMode.CANDIDATE_LIST,
            prompt = "Treat the audio as a received message and draft three short replies. Do not make commitments or invent facts not supplied by the user.",
            order = 203,
            candidates = 3,
            now = now,
        ),
    )

    private fun builtIn(
        key: String,
        title: String,
        modality: GemmaInputModality,
        task: GemmaTaskKind,
        output: GemmaOutputMode,
        prompt: String,
        order: Int,
        candidates: Int = 1,
        now: Long,
    ) = GemmaPromptTemplate(
        title = title,
        prompt = prompt,
        isEnabled = true,
        sortOrder = order,
        createdAt = now,
        updatedAt = now,
        inputModality = modality.name,
        taskKind = task.name,
        outputMode = output.name,
        candidateCount = candidates,
        showInActionMenu = true,
        isBuiltIn = true,
        builtInKey = key,
    )
}
