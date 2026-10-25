package com.kazumaproject.markdownhelperkeyboard.mushroom;

import android.app.Activity;
import android.content.Intent;
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
    private EditText editor;

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && editor != null) {
            getSystemService(InputMethodManager.class).showSoftInput(editor, 0);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getIntent().getBooleanExtra("editor", false)) {
            editor = new EditText(this);
            editor.setId(android.R.id.input);
            editor.setText("abc");
            editor.setSelection(1, 2);
            setContentView(editor);
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            editor.requestFocus();
            return;
        }
        String value = getIntent().getStringExtra(REPLACE_KEY);
        final String source = value == null ? "" : value;
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        TextView description = new TextView(this);
        description.setText("Source: " + source);
        layout.addView(description);
        for (final String label : new String[]{"Return result", "Cancel", "Missing result"}) {
            Button button = new Button(this);
            button.setText(label);
            button.setOnClickListener(view -> {
                Intent result = new Intent();
                if (label.equals("Return result")) result.putExtra(REPLACE_KEY, "[" + source + "]");
                setResult(label.equals("Cancel") ? RESULT_CANCELED : RESULT_OK, result);
                finish();
            });
            layout.addView(button);
        }
        setContentView(layout);
    }
}
