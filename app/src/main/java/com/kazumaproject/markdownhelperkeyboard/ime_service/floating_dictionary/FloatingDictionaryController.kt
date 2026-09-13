package com.kazumaproject.markdownhelperkeyboard.ime_service.floating_dictionary

import android.content.Context
import android.content.res.Configuration
import android.content.res.ColorStateList
import android.database.sqlite.SQLiteConstraintException
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import androidx.core.graphics.ColorUtils
import android.text.TextUtils
import android.os.Build
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.*
import androidx.core.widget.addTextChangedListener
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.ime_service.composing_guide.*
import com.kazumaproject.markdownhelperkeyboard.short_cut.ShortcutType
import kotlinx.coroutines.*

/** IME-owned windows: independent visibility, shared input routing, no overlay permission. */
internal class FloatingDictionaryController(
    private val context: Context,
    private val store: FloatingDictionaryStore,
    private val colors: () -> CandidatePanelColors,
    private val onInputTarget: (EditText?) -> Unit,
    private val onStateChanged: () -> Unit,
) {
    private val panels = linkedMapOf<DictionaryKind, Panel>()
    private var anchor: View? = null
    private var activeEditor: EditText? = null
    private var changingFocus = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val manager = context.getSystemService(WindowManager::class.java)
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val density get() = context.resources.displayMetrics.density
    private fun dp(value: Int) = (value * density).toInt()
    val activeShortcuts: Set<ShortcutType> get() = panels.values.filter { it.visible }.map { it.kind.shortcut }.toSet()
    private val layoutListener = android.view.ViewTreeObserver.OnGlobalLayoutListener {
        panels.values.filter { it.visible }.forEach { it.show() }
    }

    fun attach(host: View) {
        detach()
        anchor = host
        host.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
        host.post { if (anchor === host) panels.values.filter { it.visible }.forEach { it.show() } }
    }

    fun toggle(kind: DictionaryKind) {
        val panel = panels.getOrPut(kind) { Panel(kind) }
        if (panel.visible) panel.hide() else {
            panel.visible = true
            panel.show()
        }
        onStateChanged()
    }

    /** Temporarily release window tokens during configuration changes without losing drafts. */
    fun detach() {
        focus(null)
        anchor?.viewTreeObserver?.takeIf { it.isAlive }?.removeOnGlobalLayoutListener(layoutListener)
        panels.values.forEach { it.unmount() }
        anchor = null
    }

    fun endSession() {
        detach()
        panels.values.forEach { it.destroy() }
        panels.clear()
        onStateChanged()
    }

    fun destroy() { endSession(); scope.cancel() }

    private fun focus(editor: EditText?) {
        if (activeEditor === editor) return
        changingFocus = true
        try {
            val previous = activeEditor
            activeEditor = editor
            onInputTarget(editor)
            previous?.clearFocus()
            if (editor != null) editor.requestFocus()
            else (previous?.rootView as? ViewGroup)?.requestFocus()
        } finally { changingFocus = false }
    }

    private fun area(): GuideBounds {
        val rect: Rect
        if (Build.VERSION.SDK_INT >= 30) {
            val metrics = manager.currentWindowMetrics
            val inset = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            rect = Rect(metrics.bounds).apply { left += inset.left; top += inset.top; right -= inset.right; bottom -= inset.bottom }
        } else {
            val size = android.graphics.Point().also { manager.defaultDisplay.getRealSize(it) }
            val inset = anchor?.rootWindowInsets
            rect = Rect(inset?.stableInsetLeft ?: 0, inset?.stableInsetTop ?: 0,
                size.x - (inset?.stableInsetRight ?: 0), size.y - (inset?.stableInsetBottom ?: 0))
        }
        return GuideBounds(rect.left, rect.top, rect.width(), rect.height())
    }

    private inner class Panel(val kind: DictionaryKind) {
        var visible = false
        private var params: WindowManager.LayoutParams? = null
        private var bounds: GuideBounds? = null
        private var lastArea: GuideBounds? = null
        private var landscape = false
        private var editingLayout = false
        private var gesture: ComposingGuideGesture? = null
        private val palette = colors()
        private val panelScope = CoroutineScope(SupervisorJob(scope.coroutineContext[Job]) + Dispatchers.Main.immediate)
        private val root = ComposingGuideView(context,
            onEdit = ::toggleLayoutEditing,
            onTextSize = { _, _ -> },
            onHandleEvent = ::handleEvent,
            title = context.getString(kind.title),
            onHide = ::hide,
        ).apply { isFocusableInTouchMode = true; requestFocus() }
        private val body = FrameLayout(context)
        private var rows = emptyList<DictionaryEntry>()
        private var query = ""
        private val listPage = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        private val recycler = RecyclerView(context)
        private val empty = label(context.getString(R.string.floating_dictionary_empty))
        private val adapter = EntryAdapter()
        private var busy = false

        init {
            root.setColors(palette)
            root.setShowComposing(false)
            root.candidateContainer.addView(body, LinearLayout.LayoutParams(-1, -1))
            val search = editor(R.string.floating_dictionary_search).apply {
                setSingleLine(true)
                addTextChangedListener { query = it.toString(); filter() }
            }
            listPage.addView(search, LinearLayout.LayoutParams(-1, dp(48)))
            listPage.addView(button(R.string.floating_dictionary_add) { showForm(null) })
            listPage.addView(empty)
            recycler.layoutManager = LinearLayoutManager(context)
            recycler.adapter = adapter
            listPage.addView(recycler, LinearLayout.LayoutParams(-1, 0, 1f))
            body.addView(listPage, FrameLayout.LayoutParams(-1, -1))
            panelScope.launch {
                try { store.observe(kind).collect { rows = it; filter() } }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { empty.text = context.getString(R.string.update_failed); empty.visibility = View.VISIBLE }
            }
        }

        private fun label(value: String) = TextView(context).apply {
            text = value; setTextColor(palette.text); gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        private fun button(text: Int, action: () -> Unit) = Button(context).apply {
            setText(text); setTextColor(palette.text); isAllCaps = false; minHeight = dp(48)
            isFocusable = false
            backgroundTintList = ColorStateList.valueOf(palette.pressed)
            setOnClickListener { if (!busy) action() }
        }
        private fun editor(hintId: Int) = EditText(context).apply {
            hint = context.getString(hintId); contentDescription = hint
            setTextColor(palette.text); setHintTextColor(palette.text)
            textSize = 16f
            showSoftInputOnFocus = false
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            // The IME retains its application connection. Only keystrokes aimed at this field
            // use its local InputConnection, so opening an editor cannot end the IME session.
            setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    if (activeEditor === this) onInputTarget(this) else focus(this)
                }
                false
            }
            onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                if (hasFocus && !changingFocus && activeEditor !== this) focus(this)
            }
        }

        private fun filter() {
            val q = query.trim()
            adapter.items = rows.filter { q.isEmpty() || it.reading.contains(q, true) || it.word.contains(q, true) }
            empty.visibility = if (adapter.items.isEmpty()) View.VISIBLE else View.GONE
        }

        private fun showForm(original: DictionaryEntry?) {
            focus(null)
            val page = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            val scroll = ScrollView(context).apply { isFillViewport = true; addView(page) }
            val form = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
            }
            val reading = editor(R.string.floating_dictionary_reading).apply { setSingleLine(true); hint = null; setText(original?.reading.orEmpty()) }
            val word = editor(R.string.floating_dictionary_word).apply { minLines = 2; hint = null; setText(original?.word.orEmpty()) }
            val score = editor(R.string.floating_dictionary_score).apply {
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
                setText((original?.score ?: if (kind == DictionaryKind.LEARN) 3000 else 4000).toString())
            }
            val posList = context.resources.getStringArray(com.kazumaproject.core.R.array.parts_of_speech)
            val pos = Spinner(context).apply {
                adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, posList)
                setSelection(original?.pos ?: posList.indexOf("名詞").coerceAtLeast(0))
                contentDescription = context.getString(R.string.floating_dictionary_pos)
            }
            fun fieldLabel(resource: Int) = label(context.getString(resource)).apply { textSize = 12f }
            page.addView(fieldLabel(R.string.floating_dictionary_reading))
            page.addView(reading, LinearLayout.LayoutParams(-1, dp(48)))
            page.addView(fieldLabel(R.string.floating_dictionary_word))
            page.addView(word, LinearLayout.LayoutParams(-1, dp(72)))
            val details = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                visibility = View.GONE
                if (kind != DictionaryKind.LEARN) {
                    addView(fieldLabel(R.string.floating_dictionary_pos)); addView(pos)
                }
                addView(fieldLabel(R.string.floating_dictionary_score)); addView(score)
            }
            page.addView(button(if (kind == DictionaryKind.LEARN) R.string.floating_dictionary_score else R.string.floating_dictionary_details) {
                details.visibility = if (details.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            })
            page.addView(details)
            val error = label("").apply {
                textSize = 12f
                visibility = View.GONE
                accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
                addTextChangedListener { visibility = if (it.isNullOrEmpty()) View.GONE else View.VISIBLE }
            }
            form.addView(error)
            val actions = LinearLayout(context)
            form.addView(actions, LinearLayout.LayoutParams(-1, -2))
            fun action(resource: Int, callback: () -> Unit) {
                actions.addView(button(resource, callback).apply { textSize = 14f }, LinearLayout.LayoutParams(0, -2, 1f))
            }
            action(R.string.save_string) {
                focus(null)
                val value = score.text.toString().toIntOrNull()
                if (reading.text.isNullOrBlank() || word.text.isNullOrBlank() || value == null) {
                    error.text = context.getString(R.string.floating_dictionary_invalid)
                } else {
                    val entry = DictionaryEntry(original?.id ?: 0, reading.text.toString().trim(), word.text.toString(), value, pos.selectedItemPosition)
                    mutate(error, { store.save(kind, entry, original == null) }) { showList() }
                }
            }
            action(R.string.cancel_string) { showList() }
            if (original != null) action(R.string.delete_string) {
                focus(null)
                val confirm = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
                confirm.addView(label(context.getString(R.string.floating_dictionary_delete_confirm)))
                confirm.addView(label(original.word))
                val deleteError = label(""); confirm.addView(deleteError)
                confirm.addView(button(R.string.delete_string) { mutate(deleteError, { store.delete(kind, original) }) { showList() } })
                confirm.addView(button(R.string.cancel_string) { body.removeAllViews(); body.addView(form) })
                val confirmation = ScrollView(context).apply { addView(confirm) }
                body.removeAllViews(); body.addView(confirmation)
            }
            body.removeAllViews(); body.addView(form, FrameLayout.LayoutParams(-1, -1))
            focus(reading)
        }

        private fun mutate(error: TextView, action: suspend () -> Unit, success: () -> Unit) {
            busy = true
            panelScope.launch {
                try { withContext(Dispatchers.IO) { action() }; success() }
                catch (e: CancellationException) { throw e }
                catch (_: SQLiteConstraintException) { error.setText(R.string.duplicate_item_exists) }
                catch (_: Exception) { error.setText(R.string.update_failed) }
                finally { busy = false }
            }
        }
        private fun showList() {
            if (ownsEditor()) focus(null)
            body.removeAllViews(); body.addView(listPage, FrameLayout.LayoutParams(-1, -1))
        }

        fun destroy() { visible = false; unmount(); panelScope.cancel() }

        fun hide() {
            if (ownsEditor()) focus(null)
            visible = false; editingLayout = false; root.setEditing(false); unmount(); onStateChanged()
        }
        private fun ownsEditor(): Boolean {
            var view: View? = activeEditor
            while (view != null) { if (view === root) return true; view = view.parent as? View }
            return false
        }
        fun unmount() {
            gesture = null
            if (params != null) manager.removeViewImmediate(root)
            params = null
        }
        fun show() {
            val host = anchor ?: return
            if (!visible || !host.isAttachedToWindow) return
            val nextArea = area()
            if (nextArea.width < dp(200) || nextArea.height < dp(200)) {
                hide()
                Toast.makeText(context, R.string.floating_dictionary_unavailable, Toast.LENGTH_SHORT).show()
                return
            }
            val nextLandscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            if (lastArea != nextArea || landscape != nextLandscape || bounds == null) {
                lastArea = nextArea; landscape = nextLandscape; gesture = null
                bounds = loadPlacement().resolve(nextArea.x, nextArea.y, nextArea.width, nextArea.height, density, 200f)
            }
            val b = bounds ?: return
            val p = params ?: WindowManager.LayoutParams(b.width, b.height,
                WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT).apply {
                token = host.windowToken; gravity = Gravity.TOP or Gravity.LEFT
                title = context.getString(kind.title)
            }
            if (params != null && p.x == b.x && p.y == b.y && p.width == b.width && p.height == b.height) return
            p.x = b.x; p.y = b.y; p.width = b.width; p.height = b.height
            try {
                if (root.isAttachedToWindow) manager.updateViewLayout(root, p) else manager.addView(root, p)
                params = p
            } catch (_: WindowManager.BadTokenException) { hide() }
        }
        private fun key() = "floating_dictionary_${kind.name}_${if (landscape) "landscape" else "portrait"}_"
        private fun loadPlacement(): ComposingGuidePlacement {
            val k = key()
            return ComposingGuidePlacement(preferences.getFloat(k + "x", kind.ordinal * .3f),
                preferences.getFloat(k + "y", .04f + kind.ordinal * .04f),
                preferences.getFloat(k + "width", 300f), preferences.getFloat(k + "height", 400f))
        }
        private fun savePlacement() {
            val b = bounds ?: return; val a = lastArea ?: return; val k = key()
            preferences.edit().putFloat(k + "x", (b.x - a.x).toFloat() / (a.width - b.width).coerceAtLeast(1))
                .putFloat(k + "y", (b.y - a.y).toFloat() / (a.height - b.height).coerceAtLeast(1))
                .putFloat(k + "width", b.width / density).putFloat(k + "height", b.height / density).apply()
        }
        private fun toggleLayoutEditing() {
            if (ownsEditor()) focus(null)
            editingLayout = !editingLayout
            root.setEditing(editingLayout)
        }
        private fun handleEvent(event: MotionEvent) {
            val handle = root.handleAt(event.getX(event.actionIndex), event.getY(event.actionIndex))
                ?: GuideHandle.MOVE
            handle(handle, event)
        }
        private fun handle(handle: GuideHandle, event: MotionEvent): Boolean {
            val b = bounds ?: return false; val a = lastArea ?: return false
            fun point(index: Int) = GuidePoint(event.rawX + event.getX(index) - event.x, event.rawY + event.getY(index) - event.y)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    gesture = ComposingGuideGesture(b, a, dp(200), dp(200)).also { it.add(event.getPointerId(0), handle, point(0)) }
                }
                MotionEvent.ACTION_POINTER_DOWN -> gesture?.add(event.getPointerId(event.actionIndex), handle, point(event.actionIndex))
                MotionEvent.ACTION_MOVE -> { bounds = gesture?.move((0 until event.pointerCount).associate { event.getPointerId(it) to point(it) }) ?: b; show() }
                MotionEvent.ACTION_POINTER_UP -> gesture?.remove(event.getPointerId(event.actionIndex))
                MotionEvent.ACTION_UP -> { savePlacement(); gesture = null }
                MotionEvent.ACTION_CANCEL -> { bounds = gesture?.cancel() ?: b; gesture = null; show() }
            }
            return true
        }
        private inner class EntryAdapter : RecyclerView.Adapter<EntryHolder>() {
            var items = emptyList<DictionaryEntry>()
                set(value) { field = value; notifyDataSetChanged() }
            override fun getItemCount() = items.size
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EntryHolder {
                val reading = label("").apply {
                    textSize = 14f
                    setSingleLine(true)
                    ellipsize = TextUtils.TruncateAt.END
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }
                val word = label("").apply {
                    textSize = 18f
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_VERTICAL
                    minimumHeight = dp(88)
                    setPadding(dp(12), dp(8), dp(12), dp(8))
                    layoutParams = RecyclerView.LayoutParams(-1, -2).apply {
                        setMargins(dp(4), dp(4), dp(4), dp(4))
                    }
                    background = RippleDrawable(
                        ColorStateList.valueOf(ColorUtils.setAlphaComponent(palette.text, 32)),
                        GradientDrawable().apply {
                            cornerRadius = dp(10).toFloat()
                            setColor(palette.pressed)
                        }, null,
                    )
                    isClickable = true
                    isFocusable = false
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                    addView(reading, LinearLayout.LayoutParams(-1, -2))
                    addView(word, LinearLayout.LayoutParams(-1, -2))
                }
                return EntryHolder(row, reading, word)
            }
            override fun onBindViewHolder(holder: EntryHolder, position: Int) {
                val item = items[position]
                holder.reading.text = item.reading
                holder.word.text = item.word
                holder.itemView.contentDescription = "${item.reading}、${item.word}"
                holder.itemView.setOnClickListener { if (!busy) showForm(item) }
            }
        }
    }
    private class EntryHolder(view: View, val reading: TextView, val word: TextView) : RecyclerView.ViewHolder(view)
}
