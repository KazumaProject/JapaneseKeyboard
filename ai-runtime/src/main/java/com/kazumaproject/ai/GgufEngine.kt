package com.kazumaproject.ai

/** Handles are owned by the isolated AI service; cancellation is safe from another thread. */
object GgufEngine {
    init { System.loadLibrary("keyboard_ai") }

    external fun inspect(path: String): String
    external fun initialize(path: String, projectorPath: String, threads: Int, useGpu: Boolean): Long
    external fun capabilities(handle: Long): Int
    external fun resetCancellation(handle: Long)
    external fun generate(handle: Long, prompt: String, mediaPath: String, mediaType: Int, instruction: String, targetLanguage: String): String
    external fun score(handle: Long, prefix: String, candidates: Array<String>): FloatArray
    external fun cancel(handle: Long)
    external fun close(handle: Long)
}
