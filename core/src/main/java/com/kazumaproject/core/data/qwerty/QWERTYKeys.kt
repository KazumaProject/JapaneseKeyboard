package com.kazumaproject.core.data.qwerty

object QWERTYKeys {
    val DEFAULT_KEYS = listOf(
        // Top row
        'q', 'w', 'e', 'r', 't', 'y', 'u', 'i', 'o', 'p',
        // Middle row
        'a', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l',
        // Bottom row
        'z', 'x', 'c', 'v', 'b', 'n', 'm'
    )

    val NUMBER_KEYS = listOf(
        // Top row
        '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
        // Middle row
        '-', '/', ':', ';', '(', ')', '¥', '&', '@', '\"',
        // Bottom row
        '.', ',', '?', '!', '\'',
    )

    val SYMBOL_KEYS = listOf(
        // Top row
        '[', ']', '{', '}', '#', '%', '^', '*', '+', '=',
        // Middle row
        '_', '\\', '/', '~', '<', '>', '$', '€', '£', '·',
        // Bottom row
        '.', ',', '?', '!', '\'',
    )

    val DEFAULT_KEYS_JP = listOf(
        // Top row
        'q', 'w', 'e', 'r', 't', 'y', 'u', 'i', 'o', 'p',
        // Middle row
        'a', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l','ー',
        // Bottom row
        'z', 'x', 'c', 'v', 'b', 'n', 'm'
    )

    val NUMBER_KEYS_JP = listOf(
        // Top row
        '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
        // Middle row
        '-', '/', ':', '@', '(', ')', '「', '」', '￥', '&',
        // Bottom row
        '。', '、', '?', '!', '\'',
    )

    val SYMBOL_KEYS_JP = listOf(
        // Top row
        '[', ']', '{', '}', '#', '%', '^', '*', '+', '=',
        // Middle row
        '_', '/', ';', '|', '<', '>', '\"', '\'', '$', '€',
        // Bottom row
        '.', ',', '?', '!', '・',
    )
}
