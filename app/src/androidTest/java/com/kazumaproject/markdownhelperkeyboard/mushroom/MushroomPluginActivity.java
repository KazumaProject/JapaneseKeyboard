package com.kazumaproject.markdownhelperkeyboard.mushroom;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Standalone external plugin: the test APK does not package the target app's Kotlin runtime. */
public final class MushroomPluginActivity extends Activity {
    private static final String REPLACE_KEY = "replace_key";
    public static final String ACTION_CLOSE = "com.kazumaproject.markdownhelperkeyboard.mushroom.TEST_CLOSE";
    private EditText editor;
    private final BroadcastReceiver closeReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { finish(); }
    };

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && editor != null && !getIntent().getBooleanExtra("plain_editor", false)) {
            android.util.Log.i("MushroomFixture", "editor=" + editor.getText() + " selection=" + editor.getSelectionStart() + ":" + editor.getSelectionEnd());
            getSystemService(InputMethodManager.class).showSoftInput(editor, 0);
        }
    }

    @Override
    @SuppressLint("UnspecifiedRegisterReceiverFlag") // Before API 33 the legacy receiver is exported by default.
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        IntentFilter closeFilter = new IntentFilter(ACTION_CLOSE);
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            registerReceiver(closeReceiver, closeFilter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(closeReceiver, closeFilter);
        }
        if (getIntent().getBooleanExtra("editor", false)) {
            editor = new EditText(this);
            editor.setId(android.R.id.input);
            editor.setText("abc");
            editor.setSelection(1, 2);
            fitSystemBars(editor);
            setContentView(editor);
            if (!getIntent().getBooleanExtra("plain_editor", false)) {
                getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            }
            editor.requestFocus();
            return;
        }
        String value = getIntent().getStringExtra(REPLACE_KEY);
        final String source = value == null ? "" : value;
        android.util.Log.i("MushroomFixture", "source=" + source);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        TextView description = new TextView(this);
        description.setText("Source: " + source);
        layout.addView(description);
        for (final String label : new String[]{"Return result", "Cancel", "Missing result"}) {
            Button button = new Button(this);
            button.setText(label);
            button.setOnClickListener(view -> {
                android.util.Log.i("MushroomFixture", "action=" + label + " source=" + source);
                Intent result = new Intent();
                if (label.equals("Return result")) result.putExtra(REPLACE_KEY, "[" + source + "]");
                setResult(label.equals("Cancel") ? RESULT_CANCELED : RESULT_OK, result);
                finish();
            });
            layout.addView(button);
        }
        fitSystemBars(layout);
        setContentView(layout);
    }
    @Override
    protected void onDestroy() {
        unregisterReceiver(closeReceiver);
        super.onDestroy();
    }
    private void fitSystemBars(android.view.View root) {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets bars = insets.getInsets(
                    android.view.WindowInsets.Type.systemBars() | android.view.WindowInsets.Type.displayCutout());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return insets;
            });
        } else {
            root.setFitsSystemWindows(true);
        }
    }

}
