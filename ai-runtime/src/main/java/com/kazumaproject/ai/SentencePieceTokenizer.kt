package com.kazumaproject.ai

object SentencePieceTokenizer {
    init { System.loadLibrary("keyboard_ai") }

    external fun open(path: String): Long
    external fun encode(handle: Long, text: String, bos: Boolean): IntArray
    external fun close(handle: Long)
}
