package com.kazumaproject.enterparity;

import android.app.Activity;
import android.os.Bundle;
import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.view.KeyEvent;
import android.view.inputmethod.*;
import android.webkit.*;
import android.widget.*;
import org.json.*;

/** Local sink: actions are observed, never submitted to an external service. */
public class LabActivity extends androidx.activity.ComponentActivity {
    volatile boolean resumed;
    volatile JSONObject spec;
    volatile JSONObject actual;
    volatile int connections;
    final JSONArray events = new JSONArray();
    LinearLayout root;
    volatile View editor;
    EditText next;
    volatile String webText = "";

    @Override public void onResume() { super.onResume(); resumed = true; }
    @Override public void onPause() { resumed = false; super.onPause(); }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 70, 24, 0);
        if (getIntent().getBooleanExtra("automated", false)) {
            setContentView(root);
        } else {
            LinearLayout screen = new LinearLayout(this);
            screen.setOrientation(LinearLayout.VERTICAL);
            screen.setPadding(0, 60, 0, 0);
            try {
                JSONArray cases = new JSONArray(new String(readAll(getAssets().open("cases.json")), java.nio.charset.StandardCharsets.UTF_8));
                java.util.LinkedHashMap<String, JSONObject> byId = new java.util.LinkedHashMap<>();
                for (int i = 0; i < cases.length(); i++) {
                    JSONObject item = cases.getJSONObject(i);
                    byId.put(item.getString("id"), item);
                }
                EditText filter = new EditText(this);
                filter.setSingleLine(true);
                filter.setHint("Filter case IDs: maps, number/a3, compose…");
                screen.addView(filter);
                Spinner selector = new Spinner(this);
                ArrayAdapter<String> choices = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                    new java.util.ArrayList<>(byId.keySet()));
                selector.setAdapter(choices);
                screen.addView(selector);
                filter.addTextChangedListener(new android.text.TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
                    @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                        String query = text.toString().toLowerCase(java.util.Locale.ROOT);
                        choices.setNotifyOnChange(false);
                        choices.clear();
                        for (String id : byId.keySet()) {
                            if (id.toLowerCase(java.util.Locale.ROOT).contains(query)) choices.add(id);
                        }
                        choices.notifyDataSetChanged();
                    }
                    @Override public void afterTextChanged(android.text.Editable text) {}
                });
                Button load = new Button(this);
                load.setText("Load selected case");
                load.setOnClickListener(v -> {
                    String id = (String) selector.getSelectedItem();
                    if (id != null) {
                        try { configure(byId.get(id)); } catch (Exception e) { throw new RuntimeException(e); }
                    }
                });
                screen.addView(load);
                Button details = new Button(this);
                details.setText("Show EditorInfo and recorded result");
                details.setOnClickListener(v -> {
                    if (spec == null) return;
                    try { new android.app.AlertDialog.Builder(this).setMessage(result().toString(2)).setPositiveButton("OK", null).show(); }
                    catch (Exception e) { throw new RuntimeException(e); }
                });
                screen.addView(details);
            } catch (Exception e) { throw new RuntimeException(e); }
            screen.addView(root);
            setContentView(screen);
        }
    }

    static byte[] readAll(java.io.InputStream input) throws java.io.IOException {
        try (java.io.InputStream in = input; java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int size;
            while ((size = in.read(buffer)) != -1) out.write(buffer, 0, size);
            return out.toByteArray();
        }
    }

    void configure(JSONObject value) throws Exception {
        boolean reuseRaw = spec != null && spec.optString("host").equals("raw")
            && value.optString("host").equals("raw") && editor instanceof EditText;
        spec = value;
        actual = null;
        connections = 0;
        clearEvents();
        if (reuseRaw) {
            ((TextView) root.getChildAt(0)).setText(value.getString("id"));
            EditText edit = (EditText) editor;
            edit.setText("seed");
            edit.setSelection(4);
            edit.requestFocus();
            imm().restartInput(edit);
            imm().showSoftInput(edit, InputMethodManager.SHOW_IMPLICIT);
            return;
        }
        if (editor instanceof WebView) ((WebView) editor).destroy();
        root.removeAllViews();
        TextView title = new TextView(this);
        title.setText(value.getString("id"));
        root.addView(title);
        next = new EditText(this);
        next.setHint("Next field (local only)");
        String host = value.optString("host", "raw");
        if (host.equals("webview")) {
            WebView web = new WebView(this) {
                @Override public InputConnection onCreateInputConnection(EditorInfo info) {
                    InputConnection ic = super.onCreateInputConnection(info);
                    return this == editor ? observe(ic, info) : ic;
                }
            };
            web.getSettings().setJavaScriptEnabled(true);
            web.addJavascriptInterface(new Object() {
                @JavascriptInterface public void changed(String text) { if (editor == web) webText = text; }
            }, "Sink");
            web.setWebViewClient(new WebViewClient() {
                @Override public void onPageFinished(WebView view, String url) {
                    if (view != editor) return;
                    view.evaluateJavascript("var e=document.querySelector('#e');e.focus();e.setSelectionRange(4,4)", null);
                    imm().showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
                }
            });
            String[] hints = {"enter", "enter", "go", "search", "send", "next", "done", "previous"};
            boolean multi = (value.getInt("inputType") & 0x20000) != 0;
            String tag = multi ? "textarea" : "input";
            webText = "seed";
            web.loadDataWithBaseURL("https://enter-parity.invalid/", "<meta name='viewport' content='width=device-width, initial-scale=1'><" + tag + " id='e' style='font-size:24px;width:90%;height:110px' enterkeyhint='" + hints[value.getInt("imeOptions")] + "' oninput='Sink.changed(this.value)' " + (multi ? ">seed</textarea>" : "value='seed'>"), "text/html", "UTF-8", null);
            editor = web;
        } else if (host.equals("compose")) {
            editor = ComposeEditor.create(this, value.getInt("imeOptions"), (value.getInt("inputType") & 0x20000) != 0);
        } else {
            EditText edit = new EditText(this) {
                @Override public InputConnection onCreateInputConnection(EditorInfo info) {
                    InputConnection ic = super.onCreateInputConnection(info);
                    if (spec.optString("host").equals("raw")) {
                        info.inputType = spec.optInt("inputType");
                        info.imeOptions = (int) spec.optLong("imeOptions");
                        info.actionLabel = spec.isNull("actionLabel") ? null : spec.optString("actionLabel");
                        info.actionId = spec.optInt("actionId");
                        info.hintText = spec.optString("hintText", null);
                        info.fieldName = spec.optString("fieldName", null);
                        info.privateImeOptions = spec.optString("privateImeOptions", null);
                    }
                    return this == editor ? observe(ic, info) : ic;
                }
            };
            edit.setInputType(host.equals("raw") ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE : value.getInt("inputType"));
            edit.setImeOptions(value.getInt("imeOptions"));
            edit.setText("seed");
            edit.setSelection(4);
            edit.setOnEditorActionListener((v, action, event) -> {
                if (event != null) return false;
                if (action == EditorInfo.IME_ACTION_NEXT || action == EditorInfo.IME_ACTION_PREVIOUS) next.requestFocus();
                return true;
            });
            editor = edit;
        }
        root.addView(editor, new LinearLayout.LayoutParams(-1, 300));
        root.addView(next, new LinearLayout.LayoutParams(-1, 130));
        editor.requestFocus();
        editor.postDelayed(() -> imm().showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT), 30);
    }

    InputMethodManager imm() { return (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE); }

    synchronized int eventCount() { return events.length(); }
    synchronized void clearEvents() { while (events.length() > 0) events.remove(0); }

    synchronized void event(String method, Object value) {
        try { events.put(new JSONObject().put("method", method).put("value", value)); }
        catch (JSONException e) { throw new RuntimeException(e); }
    }

    InputConnection observe(InputConnection target, EditorInfo info) {
        if (target == null) return null;
        try {
            actual = new JSONObject().put("inputType", info.inputType).put("imeOptions", Integer.toUnsignedLong(info.imeOptions))
                .put("actionLabel", info.actionLabel == null ? JSONObject.NULL : info.actionLabel.toString())
                .put("fieldName", info.fieldName == null ? JSONObject.NULL : info.fieldName)
                .put("privateImeOptions", info.privateImeOptions == null ? JSONObject.NULL : info.privateImeOptions)
                .put("actionId", info.actionId).put("hintText", info.hintText == null ? JSONObject.NULL : info.hintText.toString());
            connections++;
        } catch (JSONException e) { throw new RuntimeException(e); }
        return new InputConnectionWrapper(target, false) {
            @Override public boolean commitText(CharSequence text, int cursor) { event("commitText", text.toString()); return super.commitText(text, cursor); }
            @Override public boolean sendKeyEvent(KeyEvent key) { event("sendKeyEvent", key.getAction() + ":" + key.getKeyCode()); return super.sendKeyEvent(key); }
            @Override public boolean performEditorAction(int action) { event("performEditorAction", action); return super.performEditorAction(action); }
            @Override public boolean setComposingText(CharSequence text, int cursor) { event("setComposingText", text.toString()); return super.setComposingText(text, cursor); }
            @Override public boolean finishComposingText() { event("finishComposingText", ""); return super.finishComposingText(); }
        };
    }

    synchronized JSONObject result() throws Exception {
        String text = editor instanceof EditText ? ((EditText) editor).getText().toString() : editor instanceof WebView ? webText : ComposeEditor.text();
        return new JSONObject().put("id", spec.getString("id")).put("requested", spec).put("actual", actual)
            .put("events", new JSONArray(events.toString())).put("text", text)
            .put("focus", next.hasFocus() ? "next" : "editor").put("connections", connections)
            .put("composingStart", editor instanceof EditText ? BaseInputConnection.getComposingSpanStart(((EditText) editor).getText()) : -1)
            .put("composingEnd", editor instanceof EditText ? BaseInputConnection.getComposingSpanEnd(((EditText) editor).getText()) : -1);
    }
}
