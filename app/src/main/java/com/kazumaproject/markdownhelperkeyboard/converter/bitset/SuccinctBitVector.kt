package com.kazumaproject.markdownhelperkeyboard.converter.bitset

import java.util.BitSet
import kotlin.math.min

class SuccinctBitVector(private val bitSet: BitSet) {
    // 定数: 大ブロックサイズ = 256ビット、 小ブロックサイズ = 8ビット
    private val bigBlockSize = 256
    private val smallBlockSize = 8
    private val numSmallBlocksPerBig = bigBlockSize / smallBlockSize

    // 大ブロックごとに、そのブロック開始時点での累積 1 数を記録
    private val bigBlockRanks: IntArray

    // 各大ブロック内を小ブロックに分割したときの、ブロック開始からの 1 数の差分
    private val smallBlockRanks: IntArray

    // 8 ビットの数値（0～255）ごとに、その中の 1 のビット数（popcount）を記録するテーブル
    private val popCountTable: IntArray = IntArray(256)

    // BitSet 全体の 1 の総数
    private val totalOnes: Int

    private val n: Int = bitSet.size()

    init {
        // 0～255 の各値の 1 の数を求める popcount テーブルの構築
        for (i in 0 until 256) {
            popCountTable[i] = Integer.bitCount(i)
        }

        val n = bitSet.size()
        val numBigBlocks = (n + bigBlockSize - 1) / bigBlockSize
        bigBlockRanks = IntArray(numBigBlocks)
        val numSmallBlocks = (n + smallBlockSize - 1) / smallBlockSize
        smallBlockRanks = IntArray(numSmallBlocks)

        var rank = 0
        // 大ブロックごとに累積値を計算
        for (big in 0 until numBigBlocks) {
            val bigStart = big * bigBlockSize
            // この大ブロック開始時点での累積 1 数
            bigBlockRanks[big] = rank

            // 大ブロック内を小ブロック（8 ビット単位）に分割して計算
            for (small in 0 until numSmallBlocksPerBig) {
                val globalSmallIndex = big * numSmallBlocksPerBig + small
                // 範囲外にならないようにチェック
                if (globalSmallIndex >= numSmallBlocks) break
                // 小ブロック開始時の「大ブロック内での累積 1 数」
                smallBlockRanks[globalSmallIndex] = rank - bigBlockRanks[big]
                val smallStart = bigStart + small * smallBlockSize
                // 小ブロック内の各ビットを走査して 1 をカウント
                for (j in 0 until smallBlockSize) {
                    val pos = smallStart + j
                    if (pos >= n) break
                    if (bitSet.get(pos)) {
                        rank++
                    }
                }
            }
        }
        totalOnes = rank
    }

    fun size(): Int = n

    /**
     * rank1(index): 0〜index（index 含む）までの 1 の数を返す
     */
    fun rank1(index: Int): Int {
        if (index < 0) return 0
        val n = bitSet.size()
        if (index >= n) return totalOnes

        val bigIndex = index / bigBlockSize
        val offsetInBig = index % bigBlockSize
        val smallIndex = offsetInBig / smallBlockSize
        val offsetInSmall = offsetInBig % smallBlockSize

        // 大ブロックの累積値 + 小ブロック内での差分
        val rankBase =
            bigBlockRanks[bigIndex] + smallBlockRanks[bigIndex * numSmallBlocksPerBig + smallIndex]
        var additional = 0
        val smallBlockStart = bigIndex * bigBlockSize + smallIndex * smallBlockSize
        for (i in 0..offsetInSmall) {
            if (smallBlockStart + i >= n) break
            if (bitSet.get(smallBlockStart + i)) additional++
        }
        return rankBase + additional
    }

    /**
     * rank0(index): 0〜index（index 含む）までの 0 の数を返す
     * (index + 1) - rank1(index) で求められる
     */
    fun rank0(index: Int): Int {
        if (index < 0) return 0
        val n = bitSet.size()
        if (index >= n) return n - totalOnes
        return (index + 1) - rank1(index)
    }

    /**
     * select1(nodeId): nodeId 番目の 1 のビットが現れる位置を返す（nodeId は 1 から始まる）
     */
    fun select1(nodeId: Int): Int {
        if (nodeId < 1 || nodeId > totalOnes) return -1

        // 大ブロック補助データに対する二分探索で対象の大ブロックを求める
        var lo = 0
        var hi = bigBlockRanks.size - 1
        var bigBlock = 0
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            if (bigBlockRanks[mid] < nodeId) {
                bigBlock = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        // 大ブロック開始までの 1 の数との差分が、この大ブロック内での目標となる
        val localTarget = nodeId - bigBlockRanks[bigBlock]

        // 該当大ブロック内の小ブロックを線形探索
        val baseSmallIndex = bigBlock * numSmallBlocksPerBig
        var smallBlock = 0
        while (smallBlock < numSmallBlocksPerBig - 1) {
            val nextGlobalSmallIndex = baseSmallIndex + smallBlock + 1
            if (nextGlobalSmallIndex >= smallBlockRanks.size) break

            if (smallBlockRanks[nextGlobalSmallIndex] < localTarget) {
                smallBlock++

            } else {
                break
            }
        }

        // 小ブロック内をビット単位に走査して正確な位置を求める
        val globalSmallIndex = baseSmallIndex + smallBlock
        val offsetInSmallBlock = localTarget - smallBlockRanks[globalSmallIndex]
        val smallBlockStart = bigBlock * bigBlockSize + smallBlock * smallBlockSize
        var count = 0
        for (i in 0 until smallBlockSize) {
            val pos = smallBlockStart + i
            if (pos >= bitSet.size()) break
            if (bitSet.get(pos)) {
                count++
                if (count == offsetInSmallBlock) {
                    return pos
                }
            }
        }
        return -1 // 見つからなかった場合（通常はありえない）
    }

    /**
     * select0(nodeId): nodeId 番目の 0 のビットが現れる位置を返す（nodeId は 1 から始まる）
     */
    fun select0(nodeId: Int): Int {
        val n = bitSet.size()
        val totalZeros = n - totalOnes
        if (nodeId < 1 || nodeId > totalZeros) return -1

        // 大ブロックに対する二分探索:
        // 各大ブロック開始時までの 0 の数は、(bigBlockIndex * bigBlockSize) - bigBlockRanks[bigBlockIndex]
        var lo = 0
        var hi = bigBlockRanks.size - 1
        var bigBlock = 0
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            val zerosBefore = mid * bigBlockSize - bigBlockRanks[mid]
            if (zerosBefore < nodeId) {
                bigBlock = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        val zerosBeforeBlock = bigBlock * bigBlockSize - bigBlockRanks[bigBlock]
        val localTarget = nodeId - zerosBeforeBlock

        // 大ブロック内での小ブロック線形探索:
        // 大ブロック内での小ブロック線形探索:
        val baseSmallIndex = bigBlock * numSmallBlocksPerBig
        val smallBlocksInThisBig = min(numSmallBlocksPerBig, smallBlockRanks.size - baseSmallIndex)
        var smallBlock = 0
        while (smallBlock < smallBlocksInThisBig - 1) {
            val nextZeros =
                (smallBlock + 1) * smallBlockSize - smallBlockRanks[baseSmallIndex + smallBlock + 1]
            if (nextZeros < localTarget) {
                smallBlock++
            } else {
                break
            }
        }
        val globalSmallIndex = baseSmallIndex + smallBlock
        val zerosBeforeSmall = (smallBlock * smallBlockSize) - smallBlockRanks[globalSmallIndex]
        val offsetInSmallBlock = localTarget - zerosBeforeSmall

        // 小ブロック内を 1 ビットずつ走査して目的の 0 を探す
        val smallBlockStart = bigBlock * bigBlockSize + smallBlock * smallBlockSize
        var count = 0
        for (i in 0 until smallBlockSize) {
            val pos = smallBlockStart + i
            if (pos >= n) break
            if (!bitSet.get(pos)) {
                count++
                if (count == offsetInSmallBlock) {
                    return pos
                }
            }
        }
        return -1 // 見つからなかった場合
    }
}