package com.kazumaproject.markdownhelperkeyboard.dictionary_override

import com.github.luben.zstd.ZstdInputStreamNoFinalizer
import java.io.InputStream

internal fun openZstandardDictionary(input: InputStream): InputStream =
    ZstdInputStreamNoFinalizer(input).setLongMax(22) // Match the 4 MiB window used by the packager.
