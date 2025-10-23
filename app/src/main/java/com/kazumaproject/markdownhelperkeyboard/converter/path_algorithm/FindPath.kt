package com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm

import com.kazumaproject.core.domain.extensions.isAllFullWidthNumericSymbol
import com.kazumaproject.core.domain.extensions.isAllHalfWidthNumericSymbol
import com.kazumaproject.graph.Node
import com.kazumaproject.markdownhelperkeyboard.converter.Other.BOS
import com.kazumaproject.markdownhelperkeyboard.converter.Other.NUM_OF_CONNECTION_ID
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import timber.log.Timber
import java.util.PriorityQueue

class FindPath {

    fun backwardAStar(
        graph: MutableMap<Int, MutableList<Node>>, // Adjusted type
        length: Int,
        connectionIds: ShortArray,
        n: Int
    ): MutableList<Candidate> {
        forwardDp(
            graph,
            length,
            connectionIds
        )
        val resultFinal: MutableList<Candidate> = mutableListOf()
        val foundStrings = HashSet<String>() // ★★★ 改善点：重複チェック用のHashSetを追加
        val pQueue: PriorityQueue<Pair<Node, Int>> =
            PriorityQueue(
                compareBy<Pair<Node, Int>> { it.second }    // ①総コスト
                    .thenBy { it.first.sPos }               // ②開始位置（小さいほど前）
                    .thenBy { it.first.len }                // ③単語長
                    .thenBy { System.identityHashCode(it.first) } // ④メモリ上の一意性
            )

        val eos = Pair(graph[length + 1]?.get(0) ?: return resultFinal, 0)
        pQueue.add(eos)

        while (pQueue.isNotEmpty()) {
            val node: Pair<Node, Int>? = pQueue.poll()

            node?.let {
                if (node.first.tango == "BOS") {
                    val stringFromNode = getStringFromNode(node.first)

                    // ★★★ 改善点：HashSetを使い、高速で効率的な重複チェックを行う
                    if (foundStrings.add(stringFromNode)) {
                        val candidate = Candidate(
                            string = stringFromNode,
                            type = when {
                                stringFromNode.isAllFullWidthNumericSymbol() -> (30).toByte()
                                stringFromNode.isAllHalfWidthNumericSymbol() -> (31).toByte()
                                else -> (1).toByte()
                            },
                            length = length.toUByte(),
                            score = if (stringFromNode.any { it.isDigit() }) node.second + 2000 else node.second,
                            leftId = node.first.next?.l,
                            rightId = node.first.next?.r
                        )
                        resultFinal.add(candidate)
                    }
                } else {
                    val prevNodes = getPrevNodes2(graph, node.first, node.first.sPos).flatten()
                    for (prevNode in prevNodes) {
                        val edgeScore = getEdgeCost(
                            prevNode.l.toInt(),
                            node.first.r.toInt(),
                            connectionIds
                        )
                        prevNode.g = node.first.g + edgeScore + node.first.score
                        prevNode.next = node.first
                        val result2 = Pair(prevNode, prevNode.g + prevNode.f)
                        pQueue.add(result2)
                    }
                }
                if (resultFinal.size >= n) {
                    return resultFinal
                }
            }
        }
        return resultFinal
    }

    private fun forwardDp(
        graph: MutableMap<Int, MutableList<Node>>,
        length: Int,
        connectionIds: ShortArray,
        beamWidth: Int = 20
    ) {
        for (i in 1..length + 1) {
            val nodes = graph[i]
                ?: continue

            for (node in nodes) {
                val nodeScore = node.f
                var score = Int.MAX_VALUE
                var bestPrev: Node? = null
                val prevNodes =
                    getPrevNodes(graph, node, i)

                for (prev in prevNodes) {
                    val edgeCost = getEdgeCost(
                        prev.l.toInt(),
                        node.r.toInt(),
                        connectionIds
                    )
                    // `prev.f` は (i - len) 時点で計算済みの最小コスト
                    val tempCost = prev.f + nodeScore + edgeCost
                    if (tempCost < score) {
                        score = tempCost
                        bestPrev = prev
                    }
                }
                node.prev = bestPrev
                node.f = score // `node.f` に「BOSからこのノードまでの最小コスト」が確定
            }

            // ★ 2. (新規追加) 枝刈り処理
            //    この位置(i)の全ノードのコスト計算が終わった後、
            //    コストが悪いものを捨てる
            //    (注: EOS (i = length + 1) は枝刈りしない)
            if (i <= length && nodes.size > beamWidth) {
                val originalNodeCount = nodes.size
                // .f (BOSからの最小コスト) が小さい順にソート
                nodes.sortBy { it.f }

                // 上位 `beamWidth` 件だけを残す
                val prunedNodes = nodes.take(beamWidth).toMutableList()

                // graph のリストを、枝刈り後のリストで置き換える
                graph[i] = prunedNodes
                Timber.d("    - forwardDp 枝刈り @位置$i: $originalNodeCount -> ${prunedNodes.size} ノード")
            }
        }
    }

    private fun getPrevNodes(
        graph: MutableMap<Int, MutableList<Node>>,
        node: Node,
        startPosition: Int
    ): MutableList<Node> {
        val index =
            if (node.tango == "EOS") graph.keys.maxOrNull()?.minus(1) ?: return mutableListOf()
            else startPosition - node.len
        if ((startPosition - node.len) == 0) return mutableListOf(BOS)
        if (index < 0) return mutableListOf()
        return graph[index] ?: mutableListOf()
    }

    private fun getPrevNodes2(
        graph: MutableMap<Int, MutableList<Node>>,
        node: Node,
        startPosition: Int
    ): MutableList<MutableList<Node>> {
        val index =
            if (node.tango == "EOS") graph.keys.maxOrNull()?.minus(1) ?: return mutableListOf()
            else startPosition
        if (startPosition == 0) return mutableListOf(mutableListOf(BOS))
        if (index < 0) return mutableListOf()
        return mutableListOf(graph[index] ?: mutableListOf())
    }

    private fun getEdgeCost(
        leftId: Int,
        rightId: Int,
        connectionIds: ShortArray
    ): Int {
        return connectionIds[leftId * NUM_OF_CONNECTION_ID + rightId].toInt()
    }

    private fun getStringFromNode(node: Node): String {
        var tempNode = node
        val result: MutableList<String> = mutableListOf()
        while (tempNode.tango != "EOS") {
            tempNode.next?.let {
                result.add(it.tango)
                tempNode = it
            }
        }
        return result.dropLast(1).joinToString("")
    }

    /**
     * A*アルゴリズムを用いて最適な変換候補のリストを後方探索で生成します。
     *
     * @return 候補リストと、最適候補の文節区切り位置リストのPair。
     */
    fun backwardAStarWithBunsetsu(
        graph: MutableMap<Int, MutableList<Node>>,
        length: Int,
        connectionIds: ShortArray,
        n: Int
    ): Pair<List<Candidate>, List<Int>> {
        // --- ▼▼▼ ログ追加 ▼▼▼ ---
        val totalStartTime = System.currentTimeMillis()
        Timber.d("▼ backwardAStarWithBunsetsu 開始 (入力長: $length)")

        val forwardDpStartTime = System.currentTimeMillis()
        // --- ▲▲▲ ログ追加 ▲▲▲ ---

        forwardDp(
            graph,
            length,
            connectionIds
        )

        // --- ▼▼▼ ログ追加 ▼▼▼ ---
        val forwardDpTime = System.currentTimeMillis() - forwardDpStartTime
        Timber.d("  ├─ forwardDp 処理時間: ${forwardDpTime}ms")

        val backwardAStarStartTime = System.currentTimeMillis()
        // --- ▲▲▲ ログ追加 ▲▲▲ ---

        val resultFinal: MutableList<Candidate> = mutableListOf()
        var bestBunsetsuPositions: List<Int> = emptyList()
        val foundStrings = HashSet<String>()
        val pQueue: PriorityQueue<Pair<Node, Int>> =
            PriorityQueue(
                compareBy<Pair<Node, Int>> { it.second }
                    .thenBy { it.first.sPos }
                    .thenBy { it.first.len }
                    .thenBy { System.identityHashCode(it.first) }
            )

        graph[length + 1]?.get(0)?.let { pQueue.add(Pair(it, 0)) }
            ?: return Pair(emptyList(), emptyList())

        // --- ▼▼▼ ログ用変数 ▼▼▼ ---
        var loopCount = 0
        var maxQueueSize = 0
        // --- ▲▲▲ ログ用変数 ▲▲▲ ---

        while (pQueue.isNotEmpty()) {
            // --- ▼▼▼ ログ追加 ▼▼▼ ---
            loopCount++
            maxQueueSize = maxOf(maxQueueSize, pQueue.size)
            // --- ▲▲▲ ログ追加 ▲▲▲ ---

            val node: Pair<Node, Int>? = pQueue.poll()

            node?.let {
                if (node.first.tango == "BOS") {
                    val stringFromNode = getStringFromNode(node.first)

                    if (foundStrings.add(stringFromNode)) {
                        if (resultFinal.isEmpty()) {
                            bestBunsetsuPositions = getBunsetsuPositions(node.first)
                        }

                        val candidate = Candidate(
                            string = stringFromNode,
                            type = when {
                                stringFromNode.isAllFullWidthNumericSymbol() -> (30).toByte()
                                stringFromNode.isAllHalfWidthNumericSymbol() -> (31).toByte()
                                else -> (1).toByte()
                            },
                            length = length.toUByte(),
                            score = if (stringFromNode.any { it.isDigit() }) node.second + 2000 else node.second,
                            leftId = node.first.next?.l,
                            rightId = node.first.next?.r
                        )
                        resultFinal.add(candidate)
                    }
                } else {
                    val prevNodes = getPrevNodes2(graph, node.first, node.first.sPos).flatten()
                    for (prevNode in prevNodes) {
                        val edgeScore = getEdgeCost(
                            prevNode.l.toInt(),
                            node.first.r.toInt(),
                            connectionIds
                        )
                        prevNode.g = node.first.g + edgeScore + node.first.score
                        prevNode.next = node.first
                        val result2 = Pair(prevNode, prevNode.g + prevNode.f)
                        pQueue.add(result2)
                    }
                }
                if (resultFinal.size >= n) {
                    // --- ▼▼▼ ログ追加 ▼▼▼ ---
                    val backwardAStarTime = System.currentTimeMillis() - backwardAStarStartTime
                    val totalTime = System.currentTimeMillis() - totalStartTime
                    Timber.d("  ├─ 後方A*探索 処理時間: ${backwardAStarTime}ms (早期リターン)")
                    Timber.d("  │  ├─ ループ回数: $loopCount 回")
                    Timber.d("  │  └─ pQueue最大サイズ: $maxQueueSize")
                    Timber.d("▼ backwardAStarWithBunsetsu 完了 (全体: ${totalTime}ms)")
                    // --- ▲▲▲ ログ追加 ▲▲▲ ---
                    return Pair(resultFinal, bestBunsetsuPositions)
                }
            }
        }

        // --- ▼▼▼ ログ追加 ▼▼▼ ---
        val backwardAStarTime = System.currentTimeMillis() - backwardAStarStartTime
        val totalTime = System.currentTimeMillis() - totalStartTime
        Timber.d("  ├─ 後方A*探索 処理時間: ${backwardAStarTime}ms")
        Timber.d("  │  ├─ ループ回数: $loopCount 回")
        Timber.d("  │  └─ pQueue最大サイズ: $maxQueueSize")
        Timber.d("▼ backwardAStarWithBunsetsu 完了 (全体: ${totalTime}ms)")
        // --- ▲▲▲ ログ追加 ▲▲▲ ---

        return Pair(resultFinal.sortedBy { it.score }, bestBunsetsuPositions)
    }

    /**
     * ★★★ 新規追加: 完成したノードパスを辿り、文節の区切り位置リストを計算する。
     */
    private fun getBunsetsuPositions(bosNode: Node): List<Int> {
        val positions = mutableListOf<Int>()
        var currentPosition = 0
        var tempNode = bosNode.next // BOSの次のノードから走査を開始

        while (tempNode != null && tempNode.tango != "EOS") {
            // 現在のノードの品詞(leftId)が自立語であり、かつ文頭でない場合、
            // その開始位置を区切りとして追加する。
            if (currentPosition > 0 && isIndependentWord(tempNode.l)) {
                positions.add(currentPosition)
            }
            currentPosition += tempNode.len.toInt()
            tempNode = tempNode.next
        }
        return positions
    }

    /**
     * ★★★ 新規追加: 品詞IDが自立語（文節の開始となりうる）かどうかを判定する。
     * このロジックは id.def の構造に基づいています。
     */
    private fun isIndependentWord(id: Short): Boolean {
        return when (val idInt = id.toInt()) {
            // 副詞, 接続詞, 感動詞, 接頭詞, 連体詞
            in 12..28, in 2590..2670 -> true

            // 動詞,自立
            in 577..856 -> true

            // 形容詞,自立
            in 2390..2471 -> true

            // 名詞 (ただし接尾(1937-2040)を除く)
            in 1842..2195 -> idInt !in 1937..2040

            else -> false
        }
    }

}
