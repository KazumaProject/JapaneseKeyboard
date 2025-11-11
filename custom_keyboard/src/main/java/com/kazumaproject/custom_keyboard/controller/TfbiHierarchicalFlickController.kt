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
    }

    companion object {
        private const val MAX_ANGLE_DIFFERENCE = 70.0
        private const val TAG = "TfbiHierarchical"
    }

    var listener: TfbiListener? = null
    private var attachedView: View? = null

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
        rootMap: Map<TfbiFlickDirection, TfbiFlickNode>
    ) {
        this.attachedView = view
        this.rootMap = rootMap

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
        val rMap = rootMap ?: return

        // スタックに第1階層（ルート）の情報をプッシュ
        centerStack.push(event.x to event.y)
        mapStack.push(rMap)
        highlightStack.push(TfbiFlickDirection.TAP)

        currentMap = rMap
        currentHighlight = TfbiFlickDirection.TAP

        // Petalなしのポップアップを表示
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

        // ★ Sticky（選択維持）ロジック
        val highlightTargetDirection = if (direction == TfbiFlickDirection.TAP) {
            currentHighlight // TAPに戻ったら、直前のハイライトを維持
        } else {
            direction
        }

        if (highlightTargetDirection == currentHighlight) return // ハイライト方向に変化がなければ何もしない

        // ハイライト対象のノードを取得
        val node = currentM[highlightTargetDirection]

        when (node) {
            is TfbiFlickNode.Input -> {
                // 終端ノード（文字）の場合：ハイライトを更新
                currentHighlight = highlightTargetDirection
                popupView?.highlightDirection(currentHighlight)
            }

            is TfbiFlickNode.SubMenu -> {
                // ★ サブメニューノードの場合：状態をスタックにプッシュ
                Log.d(TAG, "SubMenu triggered. Pushing new state.")

                // 【変更点】
                // 新しい階層のハイライトを TAP ではなく、
                // この SubMenu を開いた方向 (highlightTargetDirection) に設定します。
                currentHighlight = highlightTargetDirection
                currentMap = node.nextMap

                centerStack.push(event.x to event.y) // 現在地を新しい中心座標としてプッシュ
                mapStack.push(currentMap!!)
                highlightStack.push(highlightTargetDirection) // どの方向から来たかを記録

                // ポップアップをサブメニューの内容で更新
                setupStageUI(currentMap!!)

                // 【変更点】
                // UIのハイライトも、中央(TAP)ではなく、
                // 新しい currentHighlight (＝開いた方向) に設定します。
                popupView?.highlightDirection(currentHighlight)
            }

            null -> {
                // マップにない無効な方向（主にTAPに戻る途中）
                // Stickyロジックなので、ハイライトは(currentHighlight)のまま維持される
                popupView?.highlightDirection(currentHighlight)
            }
        }
    }

    private fun handleTouchUp(event: MotionEvent) {
        val currentM = currentMap ?: return
        val finalDirection = currentHighlight // 最後にハイライトしていた方向
        val node = currentM[finalDirection]

        Log.d(TAG, "TouchUp: Highlight=$finalDirection, Node=$node")

        when (node) {
            is TfbiFlickNode.Input -> {
                // 終端ノード（文字）の上で指を離した場合：その文字を入力
                listener?.onFlick(node.char)
            }

            is TfbiFlickNode.SubMenu -> {
                // サブメニューの上で指を離した場合：そのサブメニューのTAP入力を試みる
                val tapNode = node.nextMap[TfbiFlickDirection.TAP]
                if (tapNode is TfbiFlickNode.Input) {
                    Log.d(TAG, "Inputting SubMenu's default TAP: ${tapNode.char}")
                    listener?.onFlick(tapNode.char)
                }
            }

            null -> {
                // ★ TAP（中央）の上で指を離した場合
                if (finalDirection == TfbiFlickDirection.TAP) {
                    val tapNode = currentM[TfbiFlickDirection.TAP]
                    if (tapNode is TfbiFlickNode.Input) {
                        Log.d(TAG, "Inputting current map's TAP: ${tapNode.char}")
                        listener?.onFlick(tapNode.char)
                    }
                }
                // （それ以外は入力なし）
            }
        }

        // 状態をリセット
        resetState()
    }

    // --- ポップアップとUIのヘルパー ---

    /**
     * ポップアップウィンドウを表示または更新します。
     * @param anchorView ポップアップの基点となる View
     * @param showPetals 長押し時など、第1階層の全文字を表示するかどうか
     */
    private fun showPopup(
        anchorView: View,
        showPetals: Boolean
    ) {
        // すでに表示されていて、Petal表示を要求されていない場合は何もしない
        if (popupWindow?.isShowing == true && !showPetals) return
        val rootM = rootMap ?: return

        // TAP（中央）に表示する文字
        val tapNode = rootM[TfbiFlickDirection.TAP]
        val tapCharacter = (tapNode as? TfbiFlickNode.Input)?.char ?: ""

        // Petal（周囲）に表示する文字
        val petalChars = if (showPetals) {
            // 長押し時は、第1階層の終端文字をすべて表示
            rootM.mapValues { (_, node) ->
                (node as? TfbiFlickNode.Input)?.char ?: "" // サブメニューは空文字
            }
        } else {
            // 通常時はTAP文字のみ（Petalなし）
            emptyMap()
        }

        popupView = TfbiFlickPopupView(context).apply {
            setCharacters(tapCharacter, petalChars)
            highlightDirection(TfbiFlickDirection.TAP)
        }

        // (PopupWindow のセットアップロジックは TfbiStickyFlickController と同じ)
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
    /**
     * 第2階層以降のUI（ポップアップの内容）を設定します。
     */
    private fun setupStageUI(map: Map<TfbiFlickDirection, TfbiFlickNode>) {
        // 中央に表示する文字
        // labelがあればそれを使い、なければInputのcharを使う
        val tapCharacter = when (val tapNode = map[TfbiFlickDirection.TAP]) {
            is TfbiFlickNode.Input -> tapNode.char
            is TfbiFlickNode.SubMenu -> tapNode.label
                ?: (tapNode.nextMap[TfbiFlickDirection.TAP] as? TfbiFlickNode.Input)?.char ?: ""

            null -> ""
        }

        // 周囲に表示する文字
        val petalChars = map.mapValues { (dir, node) ->
            if (dir == TfbiFlickDirection.TAP) "" else { // TAPは除外
                when (node) {
                    // 終端ノードは文字を表示
                    is TfbiFlickNode.Input -> node.char

                    // ★ SubMenuの場合のロジックを変更
                    // 1. label が指定されていればそれを表示
                    // 2. なければ、そのサブメニューの中央(TAP)の文字を表示
                    // 3. それもなければ "..." を表示
                    is TfbiFlickNode.SubMenu -> {
                        node.label
                            ?: (node.nextMap[TfbiFlickDirection.TAP] as? TfbiFlickNode.Input)?.char
                            ?: "..."
                    }
                }
            }
        }

        popupView?.setCharacters(tapCharacter, petalChars)
    }

    /**
     * すべての状態とポップアップをリセットします。
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
    }

    /**
     * 座標からフリック方向を計算します。
     * (TfbiStickyFlickController と同じロジック)
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
        // TAP自体は計算対象外（enabledDirections には含まれる）
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
