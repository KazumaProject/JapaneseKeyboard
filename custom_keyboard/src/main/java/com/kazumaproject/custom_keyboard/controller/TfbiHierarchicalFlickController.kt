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

    // ▼▼▼ 追加: 色設定保持用の変数 ▼▼▼
    private var popupBackgroundColor: Int? = null
    private var popupHighlightedColor: Int? = null
    private var popupTextColor: Int? = null

    /**
     * ▼▼▼ 追加: 色を設定するメソッド ▼▼▼
     */
    fun setPopupColors(backgroundColor: Int, highlightedColor: Int, textColor: Int) {
        this.popupBackgroundColor = backgroundColor
        this.popupHighlightedColor = highlightedColor
        this.popupTextColor = textColor
    }

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

    private fun handleTouchMove(event: MotionEvent, view: View) {
        // 現在の中心座標とマップをスタックの先頭から取得
        val (centerX, centerY) = centerStack.peek() ?: return
        val currentM = currentMap ?: return

        val dx = event.x - centerX
        val dy = event.y - centerY
        val enabledDirections = currentM.keys

        // 現在のフリック方向を計算
        val direction = calculateDirection(dx, dy, flickSensitivity, enabledDirections)

        if (direction == TfbiFlickDirection.TAP) {
            // 指が中央に戻った

            // 1. ジッターガードが有効か？
            if (isJitterGuardActive) {
                isJitterGuardActive = false // ガードを解除
                popupView?.highlightDirection(currentHighlight) // ハイライトは維持
                return
            }

            // 2. ジッターガードが無効な場合 (通常の TAP 処理)
            if (currentHighlight != TfbiFlickDirection.TAP && mapStack.size > 1) {

                val entryDirection = highlightStack.peek()
                val parentMap = if (mapStack.size > 1) mapStack.elementAt(1) else rootMap ?: return
                val sourceNode = parentMap[entryDirection]

                if (sourceNode is TfbiFlickNode.SubMenu && sourceNode.cancelOnTap) {
                    // ★ キャンセル実行
                    Log.d(TAG, "CancelOnTap: Popping stack.")

                    // スタックを1段戻す
                    mapStack.pop()
                    highlightStack.pop()

                    // 親のマップとハイライト状態を復元
                    currentMap = mapStack.peek()
                    currentHighlight = highlightStack.peek() // 親のハイライト(TAP)

                    // UIを親マップに戻す
                    setupStageUI(currentMap!!)

                    popupView?.highlightDirection(currentHighlight)
                    return // イベント処理終了
                }
            }

            // --- 通常の "Sticky" 動作 ---
            popupView?.highlightDirection(currentHighlight)
            return
        }


        // --- (direction != TAP) の場合の標準フリック処理 ---

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
            listener?.onFlick(selectedNode.char)
        }

        // 1タッチの終了
        resetState()
    }

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
            KeyMode.NORMAL -> rNode.label
            KeyMode.DAKUTEN -> rNode.dakutenMap?.get(TfbiFlickDirection.TAP)
                ?.let { (it as? TfbiFlickNode.Input)?.char } ?: rNode.label

            KeyMode.HANDAKUTEN -> rNode.handakutenMap?.get(TfbiFlickDirection.TAP)
                ?.let { (it as? TfbiFlickNode.Input)?.char } ?: rNode.label
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

        // 9. ★ UI（ポップアップ）を即座に更新
        setupStageUI(this.currentMap!!)
        popupView?.highlightDirection(this.currentHighlight)
    }

    // --- ポップアップとUIのヘルパー ---

    /**
     * ポップアップウィンドウを表示または更新します。
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
            rootM
                .filterKeys { it != TfbiFlickDirection.TAP }
                .mapValues { (dir, node) ->
                    when (node) {
                        is TfbiFlickNode.Input -> node.char
                        is TfbiFlickNode.SubMenu -> {
                            node.label
                                ?: (node.nextMap[TfbiFlickDirection.TAP] as? TfbiFlickNode.Input)?.char
                                ?: ""
                        }

                        is TfbiFlickNode.StatefulKey -> {
                            Log.e(TAG, "Illegal state: StatefulKey found at Petal in root map.")
                            ""
                        }
                    }
                }
        } else {
            emptyMap()
        }

        popupView = TfbiFlickPopupView(context).apply {
            // ▼▼▼ 修正: 色設定があれば適用 ▼▼▼
            if (popupBackgroundColor != null && popupHighlightedColor != null && popupTextColor != null) {
                setColors(popupBackgroundColor!!, popupHighlightedColor!!, popupTextColor!!)
            }
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
        val petalChars = map
            .filterKeys { it != TfbiFlickDirection.TAP }
            .mapValues { (dir, node) ->
                when (node) {
                    is TfbiFlickNode.Input -> node.char
                    is TfbiFlickNode.SubMenu -> {
                        node.label
                            ?: (node.nextMap[TfbiFlickDirection.TAP] as? TfbiFlickNode.Input)?.char
                            ?: ""
                    }

                    is TfbiFlickNode.StatefulKey -> {
                        Log.e(TAG, "Illegal state: StatefulKey found at Petal in SubMenu map.")
                        ""
                    }
                }
            }

        popupView?.setCharacters(tapCharacter, petalChars)
    }

    private fun resetState() {
        popupWindow?.dismiss()
        popupWindow = null
        popupView = null
        centerStack.clear()
        mapStack.clear()
        highlightStack.clear()
        currentMap = null
        currentHighlight = TfbiFlickDirection.TAP
        isJitterGuardActive = false

        if (currentMode != KeyMode.NORMAL) {
            currentMode = KeyMode.NORMAL
            val rNode = rootNode
            if (rNode != null) {
                listener?.onModeChanged(rNode.label)
            }
        }
    }

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
