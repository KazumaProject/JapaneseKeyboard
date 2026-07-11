package com.kazumaproject.markdownhelperkeyboard.converter.graph

/** Metadata carried only by graphs built from a one-character append. */
interface IncrementalGraphMetadata {
    val reusedThroughEndIndex: Int
    val conversionSignature: Int
}
