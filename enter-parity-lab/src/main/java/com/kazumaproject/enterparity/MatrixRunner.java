package com.kazumaproject.enterparity;

import android.app.*;
import android.content.*;
import android.os.*;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.graphics.Rect;
import android.view.*;
import android.view.accessibility.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Explicit touchscreen DOWN/UP on the real IME. Never injects KEYCODE_ENTER. */
public class MatrixRunner extends Instrumentation {
    Bundle args;
    String expectedIme;
    LabActivity activeActivity;
    JSONObject lastStableKey;
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); args = arguments; start(); }
    @Override public void onStart() {
        Bundle finish = new Bundle();
        expectedIme = selectedIme();
        try {
            AccessibilityServiceInfo service = getUiAutomation().getServiceInfo();
            service.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
            getUiAutomation().setServiceInfo(service);
            String json = new String(LabActivity.readAll(getTargetContext().getAssets().open("cases.json")), StandardCharsets.UTF_8);
            JSONArray cases = new JSONArray(json);
            int start = Integer.parseInt(args.getString("start", "0"));
            int end = Math.min(cases.length(), Integer.parseInt(args.getString("end", "999999")));
            int settle = Integer.parseInt(args.getString("settle", "120"));
            String filter = args.getString("filter", "");
            File file = new File(getTargetContext().getFilesDir(), args.getString("output", "results.jsonl"));
            Intent intent = new Intent(getTargetContext(), LabActivity.class).putExtra("automated", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            LabActivity activity = (LabActivity) startActivitySync(intent);
            activeActivity = activity;
            cancelTouch(); // Recover a DOWN left by an interrupted instrumentation process.
            try (FileOutputStream out = new FileOutputStream(file, false)) {
                for (int i = start; i < end; i++) {
                    JSONObject spec = cases.getJSONObject(i);
                    if (!spec.getString("id").contains(filter)) continue;
                    requireExpectedIme();
                    JSONObject[] record = {null};
                    String screenshot = null;
                    try {
                        runOnMainSync(() -> { try { activity.configure(spec); } catch(Exception e) { throw new RuntimeException(e); } });
                        long deadline = SystemClock.uptimeMillis() + 3000;
                        boolean compose = spec.getString("host").equals("compose");
                        while (!compose && activity.actual == null && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(20);
                        if (!compose && activity.actual == null) throw new IllegalStateException("no InputConnection");
                        SystemClock.sleep(compose || spec.getString("host").equals("webview") ? 700 : settle);
                        if (compose) activity.actual = awaitTextEditorInfo();
                        if (activity.actual == null) throw new IllegalStateException("missing actual EditorInfo");
                        JSONObject key;
                        if (args.containsKey("verifiedEnterX")) {
                            if (end - start != 1 || !filter.isEmpty()) throw new IllegalArgumentException("Operator-verified coordinates require exactly one case");
                            key = new JSONObject().put("x", Integer.parseInt(args.getString("verifiedEnterX")))
                                .put("y", Integer.parseInt(args.getString("verifiedEnterY")))
                                .put("label", "operator screenshot-verified Enter");
                        } else {
                            key = awaitEnter();
                        }
                        if (key == null) throw new IllegalStateException("No accessible Enter key; no unverified coordinate fallback");
                        if (args.getString("screenshots", "false").equals("true")) {
                            screenshot = file.getName() + "-" + i + ".png";
                            android.graphics.Bitmap bitmap = getUiAutomation().takeScreenshot();
                            if (bitmap == null) throw new IllegalStateException("Screenshot unavailable");
                            try (FileOutputStream png = new FileOutputStream(new File(file.getParentFile(), screenshot))) {
                                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, png);
                            } finally { bitmap.recycle(); }
                        }
                        if (compose) activity.actual = awaitTextEditorInfo();
                        runOnMainSync(() -> { activity.clearEvents(); });
                        requireExpectedIme();
                        tap(key.getInt("x"), key.getInt("y"));
                        long responseDeadline = SystemClock.uptimeMillis() + 500;
                        while (activity.eventCount() == 0 && SystemClock.uptimeMillis() < responseDeadline) SystemClock.sleep(10);
                        SystemClock.sleep(60);
                        runOnMainSync(() -> { try { record[0] = activity.result(); } catch(Exception e) { throw new RuntimeException(e); } });
                        record[0].put("key", key).put("status", "observed");
                        if (spec.getString("host").equals("raw") && activity.eventCount() == 0) {
                            record[0].put("status", "blocked").put("error", "Touch produced no observable InputConnection event");
                        }
                    } catch (Exception e) {
                        record[0] = new JSONObject().put("id", spec.getString("id")).put("status", "blocked").put("error", e.toString());
                    }
                    String observedIme = selectedIme();
                    boolean imeChanged = !Objects.equals(expectedIme, observedIme);
                    if (imeChanged) record[0].put("status", "blocked").put("error", "Selected IME changed during capture");
                    record[0].put("index", i).put("ime", observedIme);
                    if (screenshot != null) record[0].put("screenshot", screenshot);
                    out.write((record[0].toString() + "\n").getBytes(StandardCharsets.UTF_8)); out.flush();
                    requireExpectedIme();
                    if (i % 100 == 0) { Bundle progress = new Bundle(); progress.putString("stream", "case " + i + "/" + end + "\n"); sendStatus(0, progress); }
                }
            } finally { runOnMainSync(activity::finish); }
            finish.putString("stream", "Results: " + file + "\n");
            finish(Activity.RESULT_OK, finish);
        } catch (Throwable e) {
            finish.putString("stream", android.util.Log.getStackTraceString(e)); finish(Activity.RESULT_CANCELED, finish);
        }
    }

    String selectedIme() {
        return android.provider.Settings.Secure.getString(getTargetContext().getContentResolver(), "default_input_method");
    }

    void requireExpectedIme() {
        if (activeActivity != null && !activeActivity.resumed) {
            throw new IllegalStateException("Lab left the foreground; capture stopped before sending input to another app");
        }
        if (expectedIme == null || !expectedIme.equals(selectedIme())) {
            throw new IllegalStateException("Selected IME changed; capture stopped to prevent mixed observations");
        }
    }

    JSONObject awaitTextEditorInfo() throws Exception {
        // BasicTextField is always text. During floating-window reconnection the
        // IME dump can briefly contain its empty EditorInfo (inputType/options 0).
        long deadline = SystemClock.uptimeMillis() + 3000;
        String previous = null;
        while (SystemClock.uptimeMillis() < deadline) {
            requireExpectedIme();
            JSONObject info = readEditorInfo();
            if (info != null && (info.getInt("inputType") & 15) == 1) {
                String current = info.toString();
                if (current.equals(previous)) return info;
                previous = current;
            } else {
                previous = null;
            }
            SystemClock.sleep(75);
        }
        throw new IllegalStateException("No stable actual BasicTextField EditorInfo");
    }

    JSONObject readEditorInfo() throws Exception {
        String dump;
        try (InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(
                getUiAutomation().executeShellCommand("dumpsys input_method"))) {
            dump = new String(LabActivity.readAll(in), StandardCharsets.UTF_8);
        }
        int start = dump.lastIndexOf("mInputEditorInfo:");
        if (start < 0) return null;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("inputType=0x([0-9a-f]+) imeOptions=0x([0-9a-f]+)").matcher(dump.substring(start));
        if (!matcher.find()) return null;
        return new JSONObject().put("inputType", Long.parseLong(matcher.group(1), 16))
            .put("imeOptions", Long.parseLong(matcher.group(2), 16)).put("actionLabel", JSONObject.NULL)
            .put("actionId", 0).put("hintText", JSONObject.NULL).put("fieldName", JSONObject.NULL)
            .put("privateImeOptions", JSONObject.NULL);
    }

    JSONObject awaitEnter() throws Exception {
        long deadline = SystemClock.uptimeMillis() + 3000;
        JSONObject previous = lastStableKey;
        while (SystemClock.uptimeMillis() < deadline) {
            JSONObject current = findEnter();
            if (current != null && previous != null && current.toString().equals(previous.toString())) {
                lastStableKey = current;
                return current;
            }
            previous = current;
            SystemClock.sleep(50);
        }
        return null;
    }

    boolean belongsToSelectedIme(AccessibilityNodeInfo root) {
        // A floating keyboard is a PopupWindow and may be classified as an application window.
        String ime = android.provider.Settings.Secure.getString(getTargetContext().getContentResolver(), "default_input_method");
        return root != null && ime != null && ime.contains("/") &&
            ime.substring(0, ime.indexOf('/')).contentEquals(root.getPackageName() == null ? "" : root.getPackageName());
    }

    JSONObject findEnter() throws Exception {
        for (AccessibilityWindowInfo window : getUiAutomation().getWindows()) {
            AccessibilityNodeInfo root = window.getRoot();
            if (!belongsToSelectedIme(root)) continue;
            // Query known resource IDs directly; recursively walking every key is much slower.
            for (String name : new String[]{"key_pos_ime_action", "key_enter", "key_return"}) {
                for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByViewId(root.getPackageName() + ":id/" + name)) {
                    JSONObject key = findNode(node);
                    if (key != null) return key;
                }
            }
            JSONObject result = findNode(root);
            if (result != null) return result;
        }
        return null;
    }
    JSONObject findNode(AccessibilityNodeInfo node) throws Exception {
        if (node == null) return null;
        String label = (node.getContentDescription() == null ? "" : node.getContentDescription().toString());
        String text = (node.getText() == null ? "" : node.getText().toString());
        String id = node.getViewIdResourceName();
        Rect rect = new Rect(); node.getBoundsInScreen(rect);
        if (node.isVisibleToUser() && !rect.isEmpty() && rect.centerY() > 0 &&
            rect.centerY() < getTargetContext().getResources().getDisplayMetrics().heightPixels &&
            (label.matches("(?i)(enter|return|改行|確定|完了|検索|次へ|次|前へ|前|送信|実行|go|search|next|previous|done|send|custom)(キー)?") ||
             text.matches("(?i)(return|改行|確定|完了|検索|次へ|次|前へ|前|送信|実行|go|search|next|previous|done|send|custom)") ||
             id != null && (id.endsWith("/key_enter") || id.endsWith("/enter_key") || id.endsWith("/key_pos_ime_action")))) {
            return new JSONObject().put("label", label).put("text", text).put("viewId", id)
                .put("x", rect.centerX()).put("y", rect.centerY()).put("bounds", rect.toShortString());
        }
        for (int i=0; i<node.getChildCount(); i++) { JSONObject found = findNode(node.getChild(i)); if(found != null) return found; }
        return null;
    }
    void cancelTouch() {
        long now = SystemClock.uptimeMillis();
        MotionEvent cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0, 0, 0);
        cancel.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        try { getUiAutomation().injectInputEvent(cancel, true); } finally { cancel.recycle(); }
    }

    void tap(int x, int y) {
        long time = SystemClock.uptimeMillis();
        boolean completed = false;
        try {
            for (int action : new int[]{MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP}) {
                MotionEvent event = MotionEvent.obtain(time, SystemClock.uptimeMillis(), action, x, y, 0);
                event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
                try { if (!getUiAutomation().injectInputEvent(event, true)) throw new IllegalStateException("Touch injection failed"); }
                finally { event.recycle(); }
            }
            completed = true;
        } finally { if (!completed) cancelTouch(); }
    }
}
