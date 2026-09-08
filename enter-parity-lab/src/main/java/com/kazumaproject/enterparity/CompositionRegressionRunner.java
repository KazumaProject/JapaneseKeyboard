package com.kazumaproject.enterparity;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Compare this trace to dev, not Gboard: conversion policy is explicitly NOT a parity target. */
public class CompositionRegressionRunner extends MatrixRunner {
    @Override public void onStart() {
        Bundle result = new Bundle();
        expectedIme = selectedIme();
        LabActivity activity = null;
        try {
            AccessibilityServiceInfo service = getUiAutomation().getServiceInfo();
            service.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
            getUiAutomation().setServiceInfo(service);
            activity = (LabActivity) startActivitySync(new Intent(getTargetContext(), LabActivity.class)
                .putExtra("automated", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            activeActivity = activity;
            LabActivity host = activity;
            cancelTouch();
            File output = new File(getTargetContext().getFilesDir(), args.getString("output", "composition.jsonl"));
            try (FileOutputStream out = new FileOutputStream(output)) {
                for (boolean convert : new boolean[]{false, true}) {
                    JSONObject spec = new JSONObject().put("id", convert ? "conversion" : "composition")
                        .put("host", "raw").put("inputType", 1).put("imeOptions", 6)
                        .put("actionLabel", JSONObject.NULL).put("actionId", 0);
                    runOnMainSync(() -> { try { host.configure(spec); } catch (Exception e) { throw new RuntimeException(e); } });
                    SystemClock.sleep(1500);
                    // ROMAJI profile; every character is entered through an actual on-screen key.
                    for (char letter : "watashihanihonjin".toCharArray()) {
                        int previousEvents = host.eventCount();
                        tapResource("key_" + letter);
                        long inputDeadline = SystemClock.uptimeMillis() + 1000;
                        while (host.eventCount() == previousEvents && SystemClock.uptimeMillis() < inputDeadline) SystemClock.sleep(20);
                        if (host.eventCount() == previousEvents) throw new IllegalStateException("No InputConnection callback after ROMAJI key " + letter);
                        SystemClock.sleep(80);
                    }
                    SystemClock.sleep(1500);
                    requireExpectedIme();
                    JSONObject before = snapshot(host);
                    if (before.getInt("composingStart") < 0 || before.getInt("composingEnd") <= before.getInt("composingStart")) throw new IllegalStateException("No real composing span after typing; wrong keyboard profile");
                    write(out, before, "typed");
                    if (convert) {
                        tapResource("key_space");
                        SystemClock.sleep(1000);
                        write(out, snapshot(host), "converted");
                    }
                    boolean complete = false;
                    for (int press = 1; press <= 10; press++) {
                        runOnMainSync(host::clearEvents);
                        JSONObject key = awaitEnter();
                        if (key == null) throw new IllegalStateException("Enter unavailable");
                        tap(key.getInt("x"), key.getInt("y"));
                        SystemClock.sleep(450);
                        JSONObject after = snapshot(host);
                        write(out, after, "confirm-" + press);
                        JSONArray events = after.getJSONArray("events");
                        for (int i=0; i<events.length(); i++) {
                            if (events.getJSONObject(i).getString("method").equals("performEditorAction")) {
                                throw new IllegalStateException("Editor action was sent during composition confirmation");
                            }
                        }
                        if (after.getInt("composingStart") < 0 || after.getInt("composingEnd") <= after.getInt("composingStart")) { complete = true; break; }
                    }
                    if (!complete) throw new IllegalStateException("Composition did not finish in ten Enter presses");
                    runOnMainSync(host::clearEvents);
                    JSONObject key = awaitEnter();
                    if (key == null) throw new IllegalStateException("Idle Enter unavailable");
                    tap(key.getInt("x"), key.getInt("y"));
                    SystemClock.sleep(450);
                    JSONObject idle = snapshot(host);
                    write(out, idle, "editor-enter");
                    JSONArray events = idle.getJSONArray("events");
                    int actions = 0;
                    for (int i=0; i<events.length(); i++) {
                        JSONObject event = events.getJSONObject(i);
                        if (event.getString("method").equals("performEditorAction") && event.getInt("value") == 6) actions++;
                    }
                    if (actions != 1) throw new IllegalStateException("Idle Enter must send DONE exactly once");
                }
            }
            result.putString("stream", "Results: " + output + "\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable e) {
            result.putString("stream", android.util.Log.getStackTraceString(e));
            finish(Activity.RESULT_CANCELED, result);
        } finally {
            if (activity != null) { LabActivity host = activity; runOnMainSync(host::finish); }
        }
    }

    JSONObject snapshot(LabActivity host) {
        JSONObject[] value = {null};
        runOnMainSync(() -> { try { value[0] = host.result(); } catch(Exception e) { throw new RuntimeException(e); } });
        return value[0];
    }
    void write(FileOutputStream out, JSONObject row, String stage) throws Exception {
        requireExpectedIme();
        row.put("stage", stage).put("ime", Settings.Secure.getString(getTargetContext().getContentResolver(), "default_input_method"));
        out.write((row.toString() + "\n").getBytes(StandardCharsets.UTF_8)); out.flush();
    }
    void tapResource(String name) throws Exception {
        requireExpectedIme();
        long deadline = SystemClock.uptimeMillis() + 3000;
        Rect previous = null;
        while (SystemClock.uptimeMillis() < deadline) {
            requireExpectedIme();
            for (AccessibilityWindowInfo window : getUiAutomation().getWindows()) {
                AccessibilityNodeInfo root = window.getRoot();
                if (!belongsToSelectedIme(root)) continue;
                for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByViewId(root.getPackageName() + ":id/" + name)) {
                    Rect rect = new Rect(); node.getBoundsInScreen(rect);
                    if (node.isVisibleToUser() && !rect.isEmpty() && rect.centerY() < getTargetContext().getResources().getDisplayMetrics().heightPixels) {
                        if (rect.equals(previous)) { tap(rect.centerX(), rect.centerY()); return; }
                        previous = new Rect(rect);
                    }
                }
            }
            SystemClock.sleep(50);
        }
        throw new IllegalStateException("No visible ROMAJI key " + name);
    }
}
