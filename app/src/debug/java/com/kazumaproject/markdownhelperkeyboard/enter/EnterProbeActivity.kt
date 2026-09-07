package com.kazumaproject.markdownhelperkeyboard.enter

import android.app.Activity
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.KeyEvent
import android.view.WindowManager
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Debug-only, synthetic editor. No network actions or real messages are sent. */
class EnterProbeActivity : Activity() {
    lateinit var editor: EditText
    val actions = mutableListOf<Int>()
    val calls = mutableListOf<String>()
    var actualEditorInfo = JSONObject()
    private var caseInputType = InputType.TYPE_CLASS_TEXT
    private var caseOptions = EditorInfo.IME_ACTION_NONE
    private var customId = 0
    private var customLabel: String? = null
    private var caseId = "manual"
    private var before: JSONObject? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        caseInputType = intent.getIntExtra("inputType", caseInputType)
        caseOptions = intent.getIntExtra("imeOptions", caseOptions)
        customId = intent.getIntExtra("actionId", 0)
        customLabel = intent.getStringExtra("actionLabel")
        caseId = intent.getStringExtra("caseId") ?: caseId
        val status = TextView(this).apply { text = "Enter probe: $caseId" }
        editor = object : EditText(this) {
            override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
                val target = super.onCreateInputConnection(outAttrs) ?: return null
                // Explicit fixture mode: TextView must not silently rewrite the requested flags.
                outAttrs.inputType = caseInputType
                outAttrs.imeOptions = caseOptions
                outAttrs.actionId = customId
                outAttrs.actionLabel = customLabel
                actualEditorInfo = JSONObject().put("inputType", outAttrs.inputType)
                    .put("imeOptions", outAttrs.imeOptions).put("actionId", outAttrs.actionId)
                    .put("actionLabel", outAttrs.actionLabel ?: JSONObject.NULL)
                return object : InputConnectionWrapper(target, false) {
                    override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                        calls.add("commitText:$text:$newCursorPosition")
                        return super.commitText(text, newCursorPosition)
                    }
                    override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
                        calls.add("setComposingText:$text:$newCursorPosition")
                        return super.setComposingText(text, newCursorPosition)
                    }
                    override fun finishComposingText(): Boolean {
                        calls.add("finishComposingText")
                        return super.finishComposingText()
                    }
                    override fun performEditorAction(editorAction: Int): Boolean {
                        calls.add("performEditorAction:$editorAction")
                        return super.performEditorAction(editorAction)
                    }
                    override fun sendKeyEvent(event: KeyEvent): Boolean {
                        calls.add("sendKeyEvent:${event.action}:${event.keyCode}:${event.metaState}:${event.repeatCount}:${event.flags}:${event.source}")
                        return super.sendKeyEvent(event)
                    }
                }
            }
        }.apply {
            id = android.R.id.input
            setRawInputType(if (caseInputType == InputType.TYPE_NULL) InputType.TYPE_CLASS_TEXT else caseInputType)
            minLines = 3
            textSize = 22f
            // Record actual editor actions, but allow raw Enter to edit the text normally.
            setOnEditorActionListener { _, actionId, event ->
                if (event == null) { actions.add(actionId); true } else false
            }
        }
        fun button(label: String, action: () -> Unit) = Button(this).apply {
            text = label
            isFocusable = false
            setOnClickListener { action() }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(status)
            addView(button("Capture BEFORE") { before = snapshot(); clearEvents() })
            addView(button("Save AFTER") {
                val initial = before
                if (initial == null) status.text = "Capture BEFORE first" else {
                    val file = saveObservation(initial, listOf(snapshot()), "manual")
                    status.text = file.name
                }
            })
            addView(editor, LinearLayout.LayoutParams(-1, 0, 1f))
        })
        editor.setText(intent.getStringExtra("text") ?: "")
        val selection = intent.getIntExtra("selectionStart", editor.length())
        editor.requestFocus()
        editor.setSelection(selection, intent.getIntExtra("selectionEnd", selection))
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) editor.post {
            getSystemService(InputMethodManager::class.java).showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    fun clearEvents() { actions.clear(); calls.clear() }

    fun snapshot(): JSONObject = JSONObject()
        .put("text", editor.text.toString())
        .put("selectionStart", editor.selectionStart).put("selectionEnd", editor.selectionEnd)
        .put("composingStart", BaseInputConnection.getComposingSpanStart(editor.text))
        .put("composingEnd", BaseInputConnection.getComposingSpanEnd(editor.text))
        .put("actions", JSONArray(actions)).put("calls", JSONArray(calls))

    fun saveObservation(initial: JSONObject, results: List<JSONObject>, operation: String): File {
        val ime = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        val pkg = ime.substringBefore('/')
        val version = packageManager.getPackageInfo(pkg, 0).versionName
        val data = JSONObject().put("schemaVersion", 1).put("caseId", caseId)
            .put("recordedAtEpochMillis", System.currentTimeMillis())
            .put("ime", ime).put("imeVersion", version).put("androidSdk", android.os.Build.VERSION.SDK_INT)
            .put("device", android.os.Build.MODEL).put("editorInfo", actualEditorInfo)
            .put("operation", operation).put("before", initial).put("after", JSONArray(results))
            .put("status", "observed-unreviewed")
        val directory = File(getExternalFilesDir(null), "enter-observations").apply { mkdirs() }
        return File(directory, "${caseId.replace(Regex("[^a-zA-Z0-9_-]"), "_")}-${System.currentTimeMillis()}.json")
            .apply { writeText(data.toString(2)) }
    }
}
