package com.kazumaproject.markdownhelperkeyboard.qa;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Separate test-APK activity used as the client of the debug AutofillService. */
public final class InlineAutofillLoginActivity extends Activity {
    private EditText usernameField;
    private EditText passwordField;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                        | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);

        usernameField = new EditText(this);
        usernameField.setHint("ユーザー名");
        usernameField.setInputType(
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        usernameField.setAutofillHints(View.AUTOFILL_HINT_USERNAME, View.AUTOFILL_HINT_EMAIL_ADDRESS);
        usernameField.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_YES);
        usernameField.setLayoutParams(fieldLayoutParams());

        passwordField = new EditText(this);
        passwordField.setHint("パスワード");
        passwordField.setInputType(
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordField.setAutofillHints(View.AUTOFILL_HINT_PASSWORD);
        passwordField.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_YES);
        passwordField.setLayoutParams(fieldLayoutParams());

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(24), dp(32), dp(24), dp(16));
        content.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText("Inline Autofill QA");
        title.setTextSize(24f);
        title.setTextColor(Color.BLACK);
        content.addView(title);

        TextView notice = new TextView(this);
        notice.setText("架空のテストアカウントのみを使用しています");
        notice.setTextSize(14f);
        notice.setTextColor(Color.DKGRAY);
        notice.setPadding(0, dp(8), 0, dp(16));
        content.addView(notice);
        content.addView(usernameField);
        content.addView(passwordField);
        setContentView(content);

        passwordField.postDelayed(() -> {
            passwordField.requestFocus();
            InputMethodManager inputMethodManager = getSystemService(InputMethodManager.class);
            inputMethodManager.showSoftInput(passwordField, InputMethodManager.SHOW_IMPLICIT);
            // Let the initially focused username field bind the IME first. Moving to password
            // afterwards gives the framework a live IME host token for inline rendering.
        }, 3000L);
    }

    private LinearLayout.LayoutParams fieldLayoutParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(56));
        params.bottomMargin = dp(12);
        return params;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
