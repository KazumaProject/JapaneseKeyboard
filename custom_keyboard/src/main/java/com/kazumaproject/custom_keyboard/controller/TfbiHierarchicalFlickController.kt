package com.kazumaproject.custom_keyboard.controller

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.PopupWindow
import androidx.core.graphics.drawable.toDrawable
import com.kazumaproject.custom_keyboard.data.KeyMode
import com.kazumaproject.custom_keyboard.data.TfbiFlickNode
import com.kazumaproject.custom_keyboard.view.TfbiFlickDirection
import com.kazumaproject.custom_keyboard.view.TfbiFlickPopupView
import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot


/**
 * 階層型（3段階以上）のフリック入力を処理するコントローラー。
 * 状態をスタックで管理し、「Sticky」な（選択維持型）動作を各階層で行う。
 */
@SuppressLint("ClickableViewAccessibility")
class TfbiHierarchicalFlickController(
    private val context: Context,
    private val flickSensitivity: Float
) {
    /**
     * リスナー：最終的に入力が決定した文字を通知します。
     */
    interface TfbiListener {
        fun onFlick(character: String)

        /**
         * コントローラーの内部状態が変更されたことを通知します。
         * InputMethodService はこれを受け取り、キーのラベルを "か" -> "が" などに変更します。
         */
        fun onModeChanged(newLabel: String)
    }

    companion object {
        private const val MAX_ANGLE_DIFFERENCE = 70.0
        private const val TAG = "TfbiHierarchical"
    }

    var listener: TfbiListener? = null
    private var attachedView: View? = null

    private var currentMode = KeyMode.NORMAL
    private var rootNode: TfbiFlickNode.StatefulKey? = null

    // ルートとなるマップ（アタッチ時に設定）
    private var rootMap: Map<TfbiFlickDirection, TfbiFlickNode>? = null

    // --- 状態管理スタック ---
    // 現在表示しているマップ（状態スタックの先頭）
    private var currentMap: Map<TfbiFlickDirection, TfbiFlickNode>? = null

    // フリックの中心座標 (x, y) を保持するスタック
    private val centerStack = ArrayDeque<Pair<Float, Float>>()

    // 表示するマップ (TfbiFlickNode) を保持するスタック
    private val mapStack = ArrayDeque<Map<TfbiFlickDirection, TfbiFlickNode>>()

    // 各階層でのハイライト方向を保持するスタック
    private val highlightStack = ArrayDeque<TfbiFlickDirection>()
    // ---

    // 現在のハイライト方向（全階層共通）
    private var currentHighlight: TfbiFlickDirection = TfbiFlickDirection.TAP
    private var isJitterGuardActive = false

    // ポップアップView関連
    private var popupView: TfbiFlickPopupView? = null
    private var popupWindow: PopupWindow? = null
    private lateinit var gestureDetector: GestureDetector

    /**
     * View と階層フリックのルートマップをアタッチします。
     */
    @SuppressLint("ClickableViewAccessibility")
    fun attach(
        view: View,
        node: TfbiFlickNode.StatefulKey
    ) {
        this.attachedView = view
        this.rootNode = node

        // ★ 内部状態に基づいて、アタッチするマップを決定する
        this.rootMap = getMapForCurrentMode(node)

        gestureDetector =
            GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                override fun onLongPress(e: MotionEvent) {
                    // 第1階層（スタックサイズが1）でのみ長押しポップアップを許可
                    if (mapStack.size <= 1) {
                        popupWindow?.dismiss()
                        showPopup(view, true) // Petal付きで表示
                    }
                }
            })

        view.setOnTouchListener { _, event -> handleTouchEvent(event) }
    }

    /**
     * 現在の状態に基づいて、StatefulKey から適切なマップを取得します。
     */
    private fun getMapForCurrentMode(node: TfbiFlickNode.StatefulKey): Map<TfbiFlickDirection, TfbiFlickNode> {
        return when (currentMode) {
            KeyMode.NORMAL -> node.normalMap
            KeyMode.DAKUTEN -> node.dakutenMap ?: node.normalMap // fallback
            KeyMode.HANDAKUTEN -> node.handakutenMap ?: node.normalMap // fallback
        }
    }

    /**
     * コントローラーを View からデタッチし、リソースを解放します。
     */
    fun cancel() {
        resetState()
        attachedView?.setOnTouchListener(null)
        attachedView = null
    }

    // --- タッチイベント処理 ---

    private fun handleTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        val view = attachedView ?: return false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> handleTouchDown(event, view)
            MotionEvent.ACTION_MOVE -> handleTouchMove(event, view)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> handleTouchUp(event)
        }
        return true
    }

    private fun handleTouchDown(event: MotionEvent, view: View) {
        resetState()

        val node = rootNode ?: return
        // ★ 状態に基づいて rootMap を決定
        val rMap = getMapForCurrentMode(node)

        this.rootMap = rMap
        this.currentMap = rMap

        // スタックにプッシュ
        centerStack.push(event.x to event.y)
        mapStack.push(rMap)
        highlightStack.push(TfbiFlickDirection.TAP)
        currentHighlight = TfbiFlickDirection.TAP

        showPopup(view, false)
    }

    /**
     * ★ 修正点 ★
     * SubMenu を開く時、および cancelOnTap で閉じる時に、
     * フリックの基準点(centerStack)を変更しないように修正。
     */
    private fun handleTouchMove(event: MotionEvent, view: View) {
        // 現在の中心座標とマップをスタックの先頭から取得
        // [修正点] centerStack は常に DOWN 時の [c1] のみ保持するため peek() で c1 を取得
        val (centerX, centerY) = centerStack.peek() ?: return
        val currentM = currentMap ?: return

        val dx = event.x - centerX
        val dy = event.y - centerY
        val enabledDirections = currentM.keys

        // 現在のフリック方向を計算
        val direction = calculateDirection(dx, dy, flickSensitivity, enabledDirections)

        // --- ★ 修正ロジック START ★ ---
        if (direction == TfbiFlickDirection.TAP) {
            // 指が中央に戻った

            // 1. ジッターガードが有効か？
            if (isJitterGuardActive) {
                // ジッターガードが有効な場合、これは SubMenu を開いた直後の
                // 意図しない TAP とみなし、キャンセル処理を「スキップ」する。
                isJitterGuardActive = false // ガードを解除
                popupView?.highlightDirection(currentHighlight) // ハイライトは維持
                return
            }

            // 2. ジッターガードが無効な場合 (通常の TAP 処理)
            // currentHighlight が TAP でない（＝何かを選択していた）場合のみ、
            // cancelOnTap のチェックを行う
            if (currentHighlight != TfbiFlickDirection.TAP && mapStack.size > 1) {

                val entryDirection = highlightStack.peek()
                // [修正点] スタック構造の変更に伴い、親マップの取得方法を変更
                val parentMap = if (mapStack.size > 1) mapStack.elementAt(1) else rootMap ?: return
                val sourceNode = parentMap[entryDirection]

                if (sourceNode is TfbiFlickNode.SubMenu && sourceNode.cancelOnTap) {
                    // ★ キャンセル実行
                    Log.d(TAG, "CancelOnTap: Popping stack.")

                    // スタックを1段戻す
                    // [修正点] centerStack.pop() を削除
                    mapStack.pop()
                    highlightStack.pop()

                    // 親のマップとハイライト状態を復元
                    currentMap = mapStack.peek()
                    currentHighlight = highlightStack.peek() // 親のハイライト(TAP)

                    // UIを親マップに戻す
                    if (mapStack.size == 1) {
                        showPopup(attachedView!!, false)
                    } else {
                        setupStageUI(currentMap!!)
                    }
                    popupView?.highlightDirection(currentHighlight)
                    return // イベント処理終了
                }
            }

            // --- 通常の "Sticky" 動作 ---
            popupView?.highlightDirection(currentHighlight)
            return
        }
        // --- ★ 修正ロジック END ★ ---


        // --- 以下は (direction != TAP) の場合の標準フリック処理 ---

        // (TAP 以外の方向に動いたので、ジッターガードは解除)
        isJitterGuardActive = false

        val highlightTargetDirection = direction
        if (highlightTargetDirection == currentHighlight) return // 変化なし

        // ハイライト対象のノードを取得
        val node = currentM[highlightTargetDirection]

        // ハイライトを更新
        currentHighlight = highlightTargetDirection

        when (node) {
            is TfbiFlickNode.Input -> {
                // 終端ノード（文字）の場合：ハイライトを更新
                popupView?.highlightDirection(currentHighlight)

                // 状態更新
                updateInternalState(node.triggersMode, event)
            }

            is TfbiFlickNode.SubMenu -> {
                // ★ サブメニューノードの場合：状態をスタックにプッシュ
                Log.d(TAG, "SubMenu triggered. Pushing new state.")

                currentMap = node.nextMap

                // [修正点] centerStack.push(...) を削除
                mapStack.push(currentMap!!)
                highlightStack.push(highlightTargetDirection) // どの方向から来たかを記録

                // ハイライトは開いた方向 (currentHighlight) を維持
                // ただし、ジッターガードを有効にする
                isJitterGuardActive = true

                // ポップアップをサブメニューの内容で更新
                setupStageUI(currentMap!!)
                popupView?.highlightDirection(currentHighlight)
            }

            null -> {
                // マップにない無効な方向（主にTAPに戻る途中）
                popupView?.highlightDirection(currentHighlight)
            }

            is TfbiFlickNode.StatefulKey -> {
                Log.e(TAG, "Illegal state: StatefulKey found inside a flick map during Move.")
            }
        }
    }

    /**
     * ★★★ 修正点 ★★★
     * 状態(Mode)の更新を handleTouchMove に移動したため、
     * handleTouchUp は文字入力(onFlick)のみに専念する
     */
    private fun handleTouchUp(event: MotionEvent) {
        val currentM = currentMap ?: return
        val finalDirection = currentHighlight
        val node = currentM[finalDirection]

        var selectedNode: TfbiFlickNode.Input? = null

        when (node) {
            is TfbiFlickNode.Input -> {
                selectedNode = node
            }

            is TfbiFlickNode.SubMenu -> {
                val tapNode = node.nextMap[TfbiFlickDirection.TAP]
                if (tapNode is TfbiFlickNode.Input) {
                    selectedNode = tapNode
                }
            }

            null -> {
                if (finalDirection == TfbiFlickDirection.TAP) {
                    val tapNode = currentM[TfbiFlickDirection.TAP]
                    if (tapNode is TfbiFlickNode.Input) {
                        selectedNode = tapNode
                    }
                }
            }

            is TfbiFlickNode.StatefulKey -> {
                Log.e(TAG, "Illegal state: StatefulKey found inside a flick map during Up.")
            }
        }

        if (selectedNode != null) {
            // 1. 文字を入力
            listener?.onFlick(selectedNode.char)

            // 2. ★ 変更点：
            // 状態更新は handleTouchMove で既に行われているため、
            // ここでの updateInternalState の呼び出しを削除する
            // updateInternalState(selectedNode.triggersMode) // <-- 削除
        }

        // 1タッチの終了
        resetState()
    }

    /**
     * ★ 修正点：
     * 内部状態の更新と同時に、現在操作中のマップとUIも
     * "ホットスワップ" するように変更。
     *
     * [ロジック]
     * 1. 状態が変更されたら、新しい`rootMap` (例: `g_Map`) を取得する。
     * 2. 現在の `highlightStack` (例: `[TAP, LEFT]`) を使って、
     * `newRootMap` の中を `LEFT` までたどり、
     * 新しい `SubMenu` (例: `subMenu_GI`) を見つける。
     * 3. スタック全体を、新しいマップ(g_Map, subMenu_GI.nextMap)で再構築する。
     * 4. [修正] `centerStack` (Size: 1) と `highlightStack` (Size: N) の
     * サイズが異なることを前提にスタックを再構築する。
     */
    private fun updateInternalState(newMode: KeyMode?, event: MotionEvent) {
        // 状態遷移のトリガーがなければ何もしない
        if (newMode == null) return
        // 既にそのモードなら何もしない
        if (newMode == currentMode) return

        Log.d(TAG, "Changing mode from $currentMode to $newMode")
        currentMode = newMode

        val rNode = rootNode ?: return

        // 1. ★ 状態が変わったことを InputMethodService に通知
        val newLabel = when (currentMode) {
            KeyMode.NORMAL -> rNode.label // "は"
            KeyMode.DAKUTEN -> rNode.dakutenMap?.get(TfbiFlickDirection.TAP)
                ?.let { (it as? TfbiFlickNode.Input)?.char } ?: rNode.label // "ば"
            KeyMode.HANDAKUTEN -> rNode.handakutenMap?.get(TfbiFlickDirection.TAP)
                ?.let { (it as? TfbiFlickNode.Input)?.char } ?: rNode.label // "ぱ"
        }
        listener?.onModeChanged(newLabel)

        // 2. ★ マップのホットスワップ
        Log.d(TAG, "Hot-swapping map stack to $newLabel map.")

        // 3. 新しいモードに基づいた新しい「ルートマップ」を取得
        val newRootMap = getMapForCurrentMode(rNode)
        this.rootMap = newRootMap

        // 4. 現在のスタック（パス）の情報をバックアップ
        val highlightPath = highlightStack.toList().reversed() // 例: [TAP, LEFT] (Size N)
        // [修正点] centerStack は DOWN 時の座標 1つだけ
        val originalCenter = centerStack.peek() ?: (event.x to event.y)

        // 5. スタックをクリア
        mapStack.clear()
        centerStack.clear()
        highlightStack.clear()

        // 6. バックアップしたパスを使い、新しいルートマップでスタックを再構築
        var tempMap = newRootMap
        var success = true

        // 6a. ★ centerStack を復元 (DOWN時の座標 1つだけ)
        centerStack.push(originalCenter)

        // 6b. ★ mapStack と highlightStack を復元
        for (i in highlightPath.indices) {
            val highlight = highlightPath[i]

            // スタックにプッシュ
            mapStack.push(tempMap)
            highlightStack.push(highlight)

            // 次の階層があるか？ (i < highlightPath.size - 1)
            if (i < highlightPath.size - 1) {
                val nextHighlight = highlightPath[i + 1] // 次のパス (例: LEFT)
                val nextNode = tempMap[nextHighlight]    // newRootMap[LEFT]

                if (nextNode is TfbiFlickNode.SubMenu) {
                    // 次の SubMenu マップへ
                    tempMap = nextNode.nextMap
                } else {
                    // 並列マップに SubMenu が存在しない（マップ定義エラー）
                    Log.e(
                        TAG,
                        "Map mismatch! New map doesn't have parallel SubMenu at $nextHighlight"
                    )
                    success = false
                    break
                }
            }
        }

        // 7. 最後の階層のマップを currentMap に設定
        if (success) {
            this.currentMap = tempMap
        } else {
            // 失敗したらルートに戻す
            this.currentMap = newRootMap
            mapStack.push(newRootMap)
            highlightStack.push(highlightPath.firstOrNull() ?: TfbiFlickDirection.TAP)
        }

        // 8. (削除済み)

        // 9. ★ UI（ポップアップ）を即座に更新
        setupStageUI(this.currentMap!!)
        // currentHighlight は handleTouchMove の上部で既に更新済み
        popupView?.highlightDirection(this.currentHighlight)
    }

    // --- ポップアップとUIのヘルパー ---

    /**
     * ポップアップウィンドウを表示または更新します。
     * (前回の修正を適用済み)
     */
    private fun showPopup(
        anchorView: View,
        showPetals: Boolean
    ) {
        if (popupWindow?.isShowing == true && !showPetals) return
        val rootM = rootMap ?: return

        // TAP（中央）に表示する文字
        val tapCharacter = when (val tapNode = rootM[TfbiFlickDirection.TAP]) {
            is TfbiFlickNode.Input -> tapNode.char
            is TfbiFlickNode.SubMenu -> tapNode.label
                ?: (tapNode.nextMap[TfbiFlickDirection.TAP] as? TfbiFlickNode.Input)?.char ?: ""

            null -> ""
            is TfbiFlickNode.StatefulKey -> {
                Log.e(TAG, "Illegal state: StatefulKey found at TAP in root map.")
                ""
            }
        }

        // Petal（周囲）に表示する文字
        val petalChars = if (showPetals) {
            rootM.mapValues { (dir, node) ->
                if (dir == TfbiFlickDirection.TAP) "" else { // TAPは除外
                    when (node) {
                        is TfbiFlickNode.Input -> node.char
                        is TfbiFlickNode.SubMenu -> {
                            node.label
                                ?: (node.nextMap[TfbiFlickDirection.TAP] as? TfbiFlickNode.Input)?.char
                                ?: "..."
                        }

                        is TfbiFlickNode.StatefulKey -> {
                            Log.e(TAG, "Illegal state: StatefulKey found at Petal in root map.")
                            ""
                        }
                    }
                }
            }
        } else {
            emptyMap()
        }

        popupView = TfbiFlickPopupView(context).apply {
            setCharacters(tapCharacter, petalChars)
            highlightDirection(TfbiFlickDirection.TAP)
        }

        val popupWidth = anchorView.width * 3
        val popupHeight = anchorView.height * 3
        popupWindow = PopupWindow(popupView, popupWidth, popupHeight, false).apply {
            isTouchable = false
            isFocusable = false
            setBackgroundDrawable(android.graphics.Color.TRANSPARENT.toDrawable())
            isClippingEnabled = false
        }
        if (!anchorView.isAttachedToWindow) return
        val location = IntArray(2).also { anchorView.getLocationInWindow(it) }
        val offsetX = location[0] - anchorView.width
        val offsetY = location[1] - anchorView.height
        popupWindow?.showAtLocation(anchorView, Gravity.NO_GRAVITY, offsetX, offsetY)
    }

    /**
     * 第2階層以降のUI（ポップアップの内容）を設定します。
     * (前回の修正を適用済み)
     */
    private fun setupStageUI(map: Map<TfbiFlickDirection, TfbiFlickNode>) {
        // 中央に表示する文字
        val tapCharacter = when (val tapNode = map[TfbiFlickDirection.TAP]) {
            is TfbiFlickNode.Input -> tapNode.char
            is TfbiFlickNode.SubMenu -> tapNode.label
                ?: (tapNode.nextMap[TfbiFlickDirection.TAP] as? TfbiFlickNode.Input)?.char ?: ""

            null -> ""
            is TfbiFlickNode.StatefulKey -> {
                Log.e(TAG, "Illegal state: StatefulKey found at TAP in SubMenu map.")
                ""
            }
        }

        // 周囲に表示する文字
        val petalChars = map.mapValues { (dir, node) ->
            if (dir == TfbiFlickDirection.TAP) "" else { // TAPは除外
                when (node) {
                    is TfbiFlickNode.Input -> node.char
                    is TfbiFlickNode.SubMenu -> {
                        node.label
                            ?: (node.nextMap[TfbiFlickDirection.TAP] as? TfbiFlickNode.Input)?.char
                            ?: "..."
                    }

                    is TfbiFlickNode.StatefulKey -> {
                        Log.e(TAG, "Illegal state: StatefulKey found at Petal in SubMenu map.")
                        ""
                    }
                }
            }
        }

        popupView?.setCharacters(tapCharacter, petalChars)
    }

    /**
     * 1回のタッチ操作（Down->Up）内の状態をリセットします。
     * ★ 修正点：ジッターガードフラグもリセット
     */
    private fun resetState() {
        popupWindow?.dismiss()
        popupWindow = null
        popupView = null
        centerStack.clear()
        mapStack.clear()
        highlightStack.clear()
        currentMap = null
        currentHighlight = TfbiFlickDirection.TAP
        isJitterGuardActive = false // ★ ジッターガードをリセット

        // ★ 変更点：
        // 1回のタッチ操作が終了したら、必ず NORMAL モードに戻す
        if (currentMode != KeyMode.NORMAL) {
            currentMode = KeyMode.NORMAL

            // ★ 状態が変わったことを InputMethodService (FlickKeyboardView) に通知
            // （ラベルを "が" -> "か" に戻すため）
            val rNode = rootNode
            if (rNode != null) {
                // 'rNode.label' は "か" "は" などの通常ラベル
                listener?.onModeChanged(rNode.label)
            }
        }
    }

    /**
     * 座標からフリック方向を計算します。
     */
    private fun calculateDirection(
        dx: Float,
        dy: Float,
        threshold: Float,
        enabledDirections: Set<TfbiFlickDirection>
    ): TfbiFlickDirection {
        val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (distance < threshold) {
            return TfbiFlickDirection.TAP
        }
        if (enabledDirections.size <= 1 && enabledDirections.contains(TfbiFlickDirection.TAP)) {
            return TfbiFlickDirection.TAP
        }

        val centerAngles = mapOf(
            TfbiFlickDirection.RIGHT to 0.0,
            TfbiFlickDirection.DOWN_RIGHT to 35.0,
            TfbiFlickDirection.DOWN to 90.0,
            TfbiFlickDirection.DOWN_LEFT to 125.0,
            TfbiFlickDirection.LEFT to 180.0,
            TfbiFlickDirection.UP_LEFT to -125.0,
            TfbiFlickDirection.UP to -90.0,
            TfbiFlickDirection.UP_RIGHT to -35.0
        )

        val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble()))

        val closestDirectionData = enabledDirections.mapNotNull { direction ->
            val targetAngle = centerAngles[direction] ?: return@mapNotNull null // TAP は除外
            var diff = abs(angle - targetAngle)
            if (diff > 180) {
                diff = 360 - diff
            }
            Pair(direction, diff)
        }.minByOrNull { it.second }

        if (closestDirectionData == null || closestDirectionData.second > MAX_ANGLE_DIFFERENCE) {
            return TfbiFlickDirection.TAP
        }

        return closestDirectionData.first
    }
}
