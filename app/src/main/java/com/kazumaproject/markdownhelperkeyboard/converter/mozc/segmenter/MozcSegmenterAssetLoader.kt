package com.kazumaproject.markdownhelperkeyboard.converter.mozc.segmenter

import android.content.Context

class MozcSegmenterAssetLoader(
    private val context: Context,
) {
    fun load(): MozcKotlinSegmenter {
        val meta = context.assets
            .open("mozc/segmenter/segmenter_meta.dat")
            .use { it.readBytes() }
        val compressedLSize = meta.readUInt32Le(0)
        val compressedRSize = meta.readUInt32Le(4)
        val posSize = meta.readUInt32Le(8)
        val lTable = context.assets
            .open("mozc/segmenter/segmenter_ltable.dat")
            .use { it.readBytes() }
            .toShortArrayLe()
        val rTable = context.assets
            .open("mozc/segmenter/segmenter_rtable.dat")
            .use { it.readBytes() }
            .toShortArrayLe()
        val bitarray = context.assets
            .open("mozc/segmenter/segmenter_bitarray.dat")
            .use { it.readBytes() }
        val boundary = context.assets
            .open("mozc/segmenter/boundary.dat")
            .use { it.readBytes() }
            .toShortArrayLe()

        require(lTable.size == posSize)
        require(rTable.size == posSize)
        require(boundary.size == posSize * 2)

        return MozcKotlinSegmenter(
            compressedLSize = compressedLSize,
            compressedRSize = compressedRSize,
            lTable = lTable,
            rTable = rTable,
            bitarray = bitarray,
            boundaryData = boundary,
        )
    }
}

fun ByteArray.readUInt32Le(offset: Int): Int {
    require(offset >= 0 && offset + 3 < size)
    return (this[offset].toInt() and 0xff) or
            ((this[offset + 1].toInt() and 0xff) shl 8) or
            ((this[offset + 2].toInt() and 0xff) shl 16) or
            ((this[offset + 3].toInt() and 0xff) shl 24)
}

fun ByteArray.toShortArrayLe(): ShortArray {
    require(size % 2 == 0)
    return ShortArray(size / 2) { index ->
        val offset = index * 2
        (((this[offset].toInt() and 0xff) or
                ((this[offset + 1].toInt() and 0xff) shl 8))).toShort()
    }
}
