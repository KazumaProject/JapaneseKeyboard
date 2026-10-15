package com.kazumaproject.markdownhelperkeyboard.ime_service.fever

import android.content.Context
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.preference.PreferenceManager
import com.kazumaproject.core.ui.skin.FeverKeyPulse

/** Owns transient effects only; text commits and gesture decisions remain with the IME. */
internal class FeverController(private val context: Context, private val feedbackAllowed: () -> Triple<Boolean, Boolean, Int>) {
    private val engine = FeverEngine()
    private val handler = Handler(Looper.getMainLooper())
    private var feedback: FeverFeedback? = null
    private var surface: FeverEffectView? = null
    private var host: ViewGroup? = null
    private var anchor: View? = null
    private var protectedKeyboard: (() -> View?)? = null
    private var keyboardContent: (() -> View?)? = null
    private var edgeSurface: FeverEffectView? = null
    private var edgeHost: ViewGroup? = null
    private var protectFloatingPanel = false
    private var ribbonHost: ViewGroup? = null
    private var floating = false
    internal val needsRibbon get() = active && !floating
    private var active = false
    private var depth = 0
    private var operation = 0L
    private var character = false
    private var suppress = false
    private var failedCommit = false
    private var confirmation = false
    private var cue: FeverCue? = null
    private var candidatesVisible = false
    private val revealCandidates = Runnable { if (active && candidatesVisible) render(FeverEvent(engine.combo, cue = FeverCue.CANDIDATES)) }
    private var composingText = ""
    internal val combo get() = engine.combo
    internal val enabled get() = active
    internal val observesSoftwareInput get() = active && depth > 0
    internal var eventObserver: ((FeverEvent) -> Unit)? = null
    private val commits = StringBuilder()
    private var touchX: Float? = null
    private var touchY: Float? = null
    private var lastTouchLight = 0L
    var settings = FeverSettings(); private set
    val wantsFullScreen get() = active && settings.fullScreen
    private val expire = Runnable { if (engine.expireCombo()) surface?.setCombo(0) }
    private var geometryDirty = true
    private val geometryBounds = Array(4) { android.graphics.Rect() }
    private val geometryPoint = IntArray(2)
    private val geometryViews = arrayOfNulls<View>(4)
    private val observedHosts = ArrayList<View>(2)
    private val globalLayout = android.view.ViewTreeObserver.OnGlobalLayoutListener { geometryDirty = true }
    private val preDraw = android.view.ViewTreeObserver.OnPreDrawListener { position(); true }
    private val layout = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        geometryDirty = true; position()
    }

    private fun clearGeometryObservers() {
        // A ViewTreeObserver can be merged and killed when its view attaches. Resolve the
        // current observer from the view so teardown also removes migrated listeners.
        observedHosts.map { it.viewTreeObserver }.distinct().forEach { tree -> if (tree.isAlive) {
            tree.removeOnGlobalLayoutListener(globalLayout); tree.removeOnPreDrawListener(preDraw)
        } }
        observedHosts.clear()
    }
    private fun observeGeometry() {
        clearGeometryObservers()
        listOfNotNull(host, edgeHost).distinctBy { it.viewTreeObserver }.forEach { parent ->
            parent.viewTreeObserver.addOnGlobalLayoutListener(globalLayout)
            parent.viewTreeObserver.addOnPreDrawListener(preDraw)
            observedHosts.add(parent)
        }
        geometryDirty = true
    }

    private fun updateGeometryBounds(index: Int, item: View?): Boolean {
        val bounds = geometryBounds[index]
        val replaced = geometryViews[index] !== item
        geometryViews[index] = item
        if (item == null) return (replaced || !bounds.isEmpty).also { bounds.setEmpty() }
        item.getLocationOnScreen(geometryPoint)
        val x = geometryPoint[0]; val y = geometryPoint[1]
        val changed = replaced || bounds.left != x || bounds.top != y ||
            bounds.width() != item.width || bounds.height() != item.height
        if (changed) bounds.set(x, y, x + item.width, y + item.height)
        return changed
    }
    /** Layout invalidates the hierarchy cache; screen bounds also catch floating-panel translations. */
    private fun geometryChanged(parent: View, content: View?, protected: View?): Boolean {
        var changed = geometryDirty
        changed = updateGeometryBounds(0, parent) || changed
        changed = updateGeometryBounds(1, content) || changed
        changed = updateGeometryBounds(2, protected) || changed
        changed = updateGeometryBounds(3, edgeHost) || changed
        geometryDirty = false
        return changed
    }

    fun configure(enabled: Boolean, mainHost: ViewGroup?, keyboard: View?, floatingHost: ViewGroup?, fullScreenHost: ViewGroup? = mainHost, protectedKeys: (() -> View?)? = null) {
        val next = FeverSettings.read(PreferenceManager.getDefaultSharedPreferences(context))
        val nextHost = if (next.fullScreen) fullScreenHost else (floatingHost?.rootView as? ViewGroup) ?: mainHost
        if (settings != next || active != enabled || host !== nextHost) stop()
        settings = next; engine.configure(next); active = enabled
        val previousRibbon = needsRibbon
        floating = floatingHost != null
        if (ribbonHost !== mainHost || previousRibbon != needsRibbon) mainHost?.requestLayout()
        ribbonHost = mainHost
        keyboardContent = protectedKeys
        protectedKeyboard = if (next.fullScreen && floatingHost != null) ({ floatingHost.rootView }) else protectedKeys
        protectFloatingPanel = next.fullScreen && floatingHost != null
        if (!enabled || nextHost == null) return
        if (host !== nextHost) {
            host = nextHost; anchor = nextHost
            surface = FeverEffectView(context).also { nextHost.overlay.add(it) }
            if (feedback == null) feedback = FeverFeedback(context)
            nextHost.addOnLayoutChangeListener(layout)
            observeGeometry()
        }
        val extraHost = (floatingHost?.rootView as? ViewGroup)?.takeIf { next.fullScreen && it !== nextHost }
        if (extraHost !== edgeHost) {
            edgeSurface?.let { edgeHost?.overlay?.remove(it) }
            edgeHost = extraHost
            edgeSurface = extraHost?.let { panel -> FeverEffectView(context).also(panel.overlay::add) }
            observeGeometry()
        }
        surface?.intensity = next.intensity
        edgeSurface?.intensity = next.intensity
        position()
    }
    private fun position() {
        val parent = host ?: return
        val view = surface ?: return
        val content = keyboardContent?.invoke()
        val protectedView = protectedKeyboard?.invoke()
        if (!geometryChanged(parent, content, protectedView)) return
        view.layout(0, 0, parent.width, parent.height)
        val target = anchor ?: parent
        val parentPosition = IntArray(2).also(parent::getLocationOnScreen)
        val position = IntArray(2).also(target::getLocationOnScreen)
        view.setArena(RectF((position[0] - parentPosition[0]).toFloat(), (position[1] - parentPosition[1]).toFloat(),
            (position[0] - parentPosition[0] + target.width).toFloat(), (position[1] - parentPosition[1] + target.height).toFloat()))
        val protected = protectedView?.let { keys ->
            val point = IntArray(2).also(keys::getLocationOnScreen)
            RectF((point[0] - parentPosition[0]).toFloat(), (point[1] - parentPosition[1]).toFloat(),
                (point[0] - parentPosition[0] + keys.width).toFloat(),
                if (protectFloatingPanel) (point[1] - parentPosition[1] + keys.height).toFloat() else parent.height.toFloat())
        }
        view.protectKeys(protected)
        val keys = ArrayList<RectF>()
        fun collect(candidate: View) {
            if (!candidate.isShown || candidate.width <= 0 || candidate.height <= 0) return
            val keyImage = candidate is android.widget.ImageView && candidate.id != View.NO_ID &&
                runCatching { candidate.resources.getResourceEntryName(candidate.id).startsWith("key_") }.getOrDefault(false)
            if ((candidate is android.widget.Button || keyImage) && candidate.background != null) {
                val point = IntArray(2).also(candidate::getLocationOnScreen)
                val rect = RectF((point[0] - parentPosition[0]).toFloat(), (point[1] - parentPosition[1]).toFloat(),
                    (point[0] - parentPosition[0] + candidate.width).toFloat(), (point[1] - parentPosition[1] + candidate.height).toFloat())
                if (protected != null && RectF.intersects(rect, protected)) keys.add(rect)
            } else if (candidate is ViewGroup) for (index in 0 until candidate.childCount) collect(candidate.getChildAt(index))
        }
        content?.let(::collect)
        val controls = ArrayList<RectF>()
        if (floating && !settings.fullScreen && content != null) {
            val body = IntArray(2).also(content::getLocationOnScreen)
            fun collectControls(candidate: View) {
                if (!candidate.isShown) return
                val point = IntArray(2).also(candidate::getLocationOnScreen)
                if (candidate.isClickable && (candidate is android.widget.ImageView || candidate is android.widget.Button) &&
                    point[1] + candidate.height <= body[1]) {
                    controls.add(RectF((point[0] - parentPosition[0]).toFloat(), (point[1] - parentPosition[1]).toFloat(),
                        (point[0] - parentPosition[0] + candidate.width).toFloat(), (point[1] - parentPosition[1] + candidate.height).toFloat()))
                } else if (candidate is ViewGroup) for (index in 0 until candidate.childCount) collectControls(candidate.getChildAt(index))
            }
            collectControls(parent)
        }
        view.protectControls(controls)
        val candidates = content?.let { body ->
            val boxes = listOf(com.kazumaproject.markdownhelperkeyboard.R.id.suggestion_recycler_view,
                com.kazumaproject.markdownhelperkeyboard.R.id.candidates_row_view).mapNotNull { id ->
                body.findViewById<View>(id)?.takeIf { it.isShown && it.width > 0 && it.height > 0 }?.let { row ->
                    val point = IntArray(2).also(row::getLocationOnScreen)
                    RectF((point[0] - parentPosition[0]).toFloat(), (point[1] - parentPosition[1]).toFloat(),
                        (point[0] - parentPosition[0] + row.width).toFloat(), (point[1] - parentPosition[1] + row.height).toFloat())
                }
            }
            boxes.firstOrNull()?.let { first -> RectF(first).apply { boxes.drop(1).forEach(::union) } }
        }
        view.setKeyEdges(keys, candidates)
        edgeHost?.let { panel ->
            val point = IntArray(2).also(panel::getLocationOnScreen)
            val dx = (parentPosition[0] - point[0]).toFloat()
            val dy = (parentPosition[1] - point[1]).toFloat()
            edgeSurface?.apply {
                layout(0, 0, panel.width, panel.height)
                setArena(RectF(0f, 0f, panel.width.toFloat(), panel.height.toFloat()))
                protectKeys(RectF(0f, 0f, panel.width.toFloat(), panel.height.toFloat()))
                setKeyEdges(keys.map { RectF(it).apply { offset(dx, dy) } }, candidates?.let { RectF(it).apply { offset(dx, dy) } })
            }
        }
    }
    fun touch(event: MotionEvent, before: Boolean) {
        if (before) {
            val outer = depth == 0
            begin()
            if (active) {
                touchX = event.rawX; touchY = event.rawY
                if (outer) {
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) position()
                    val accent = FeverVisualStyle.accent(FeverEvent(engine.combo + 1))
                    val now = android.os.SystemClock.uptimeMillis()
                    if (event.actionMasked != MotionEvent.ACTION_CANCEL &&
                        (event.actionMasked != MotionEvent.ACTION_MOVE || now - lastTouchLight >= 65)) {
                        FeverKeyPulse.pulse(accent, settings.intensity); lastTouchLight = now
                    }
                    fun dispatch(view: FeverEffectView?, parent: View?) {
                        val point = IntArray(2).also { parent?.getLocationOnScreen(it) }
                        view?.touchPhase(event.actionMasked, event.rawX - point[0], event.rawY - point[1], accent)
                    }
                    dispatch(surface, host); dispatch(edgeSurface, edgeHost)
                }
            }
        } else end()
    }
    fun begin() {
        if (depth++ == 0) { operation++; character = false; suppress = false; failedCommit = false; confirmation = false; cue = null; commits.setLength(0) }
    }
    fun noteCharacter() { if (active && depth > 0) character = true }
    fun noteConfirmation() { if (active && depth > 0) confirmation = true }
    fun candidatesShown(visible: Boolean) {
        if (!active || candidatesVisible == visible) return
        candidatesVisible = visible
        handler.removeCallbacks(revealCandidates)
        if (visible) handler.postDelayed(revealCandidates, 32)
    }
    fun noteCue(value: FeverCue) {
        if (!active) return
        if (depth == 0) render(FeverEvent(engine.combo, cue = value))
        else if (cue == null || value.priority > cue!!.priority) cue = value
    }
    fun composing(text: CharSequence?, success: Boolean) {
        if (active && success) composingText = text?.toString().orEmpty()
    }
    fun finished(success: Boolean) {
        val text = composingText
        if (success) composingText = ""
        if (confirmation || character) committed(text, success)
    }
    fun suppressCommit() { if (depth > 0) suppress = true }
    fun committed(text: CharSequence?, success: Boolean, symbol: Boolean = false) {
        if (success) composingText = ""
        if (active && depth > 0 && !success && !text.isNullOrBlank()) failedCommit = true
        if (active && depth > 0 && success && !text.isNullOrBlank() && !suppress) {
            commits.append(text)
            if (symbol) character = true
        }
    }
    fun end() {
        if (depth <= 0 || --depth != 0) return
        if (active && !suppress) {
            if (character && (!failedCommit || commits.isNotEmpty())) {
                engine.character(operation)?.let(::render)
                handler.removeCallbacks(expire); handler.postDelayed(expire, settings.comboTimeoutMillis)
            } else cue?.let { render(FeverEvent(engine.combo, cue = it)) }
            if (commits.isNotEmpty()) engine.committed(operation, commits.toString(), true)?.let(::render)
        }
        commits.setLength(0)
    }
    private fun render(event: FeverEvent) {
        position()
        FeverKeyPulse.pulse(FeverVisualStyle.accent(event), settings.intensity, event.award != null)
        eventObserver?.invoke(event)
        val point = IntArray(2).also { host?.getLocationOnScreen(it) }
        surface?.show(event, touchX?.minus(point[0]), touchY?.minus(point[1]))
        edgeHost?.let { panel ->
            val location = IntArray(2).also(panel::getLocationOnScreen)
            edgeSurface?.show(event, touchX?.minus(location[0]), touchY?.minus(location[1]))
        }
        run {
            val allowed = feedbackAllowed()
            if (feedback == null && ((settings.sound && allowed.first) || (settings.vibration && allowed.second))) feedback = FeverFeedback(context)
            feedback?.playEvent(event, settings, allowed.first, allowed.second, allowed.third)
        }
    }
    fun stop() {
        val expandedHost = host?.takeIf { wantsFullScreen }
        active = false; engine.reset(); handler.removeCallbacks(expire); FeverKeyPulse.clear()
        candidatesVisible = false; handler.removeCallbacks(revealCandidates)
        surface?.let { it.clear(); host?.overlay?.remove(it) }
        edgeSurface?.let { it.clear(); edgeHost?.overlay?.remove(it) }
        edgeSurface = null; edgeHost = null; keyboardContent = null
        clearGeometryObservers(); geometryViews.fill(null); geometryDirty = true
        host?.removeOnLayoutChangeListener(layout)
        expandedHost?.requestLayout()
        ribbonHost?.requestLayout(); ribbonHost = null
        surface = null; host = null; anchor = null; protectedKeyboard = null; touchX = null; touchY = null
        commits.setLength(0); composingText = ""; character = false; confirmation = false; cue = null; feedback?.stop()
    }
    fun release() { stop(); feedback?.release(); feedback = null }
}

/** The outer transaction includes candidates, toolbar actions and the keyboard's child callbacks. */
internal class FeverInputFrameLayout(context: Context, internal val controller: FeverController) : android.widget.FrameLayout(context) {
    private var ribbonHeight = 0
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        ribbonHeight = if (controller.needsRibbon) (32 * resources.displayMetrics.density).toInt() else 0
        val wanted = measuredHeight + ribbonHeight
        val limited = resolveSize(wanted, heightMeasureSpec)
        ribbonHeight = (limited - measuredHeight).coerceAtLeast(0)
        setMeasuredDimension(measuredWidth, limited)
    }
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (ribbonHeight > 0) for (index in 0 until childCount) {
            val child = getChildAt(index)
            val params = child.layoutParams as LayoutParams
            val childTop = maxOf(ribbonHeight, height - child.measuredHeight - params.bottomMargin)
            child.layout(child.left, childTop, child.right, childTop + child.measuredHeight)
        }
    }
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        controller.touch(event, true)
        return try { super.dispatchTouchEvent(event) } finally { controller.touch(event, false) }
    }
}

/** Android's inputArea is WRAP_CONTENT even inside a tall IME window. Fill its AT_MOST
 * budget for effects, while the original input container keeps its own measured height. */
internal class FeverWindowFrameLayout(context: Context, private val controller: FeverController) : android.widget.FrameLayout(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val height = if (controller.wantsFullScreen && MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED)
            MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec), MeasureSpec.EXACTLY)
        else heightMeasureSpec
        super.onMeasure(widthMeasureSpec, height)
    }
}
