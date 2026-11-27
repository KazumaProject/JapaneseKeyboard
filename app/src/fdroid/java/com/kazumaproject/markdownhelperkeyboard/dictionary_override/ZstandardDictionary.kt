package com.kazumaproject.markdownhelperkeyboard.dictionary_override

import java.io.IOException
import java.io.InputStream

internal fun openZstandardDictionary(@Suppress("UNUSED_PARAMETER") input: InputStream): InputStream =
    throw IOException("Zstandard dictionaries are unavailable in F-Droid builds")
