package dev.pr984.soraprobe;
import android.app.Activity;
import android.os.Bundle;
import android.view.inputmethod.InputMethodManager;
import io.github.rosemoe.sora.widget.CodeEditor;
public class HostActivity extends Activity {
 public CodeEditor editor;
 @Override public void onCreate(Bundle state) {
  super.onCreate(state);
  editor = new CodeEditor(this);
  editor.getProps().disallowSuggestions = false;
  editor.setDisableSoftKbdIfHardKbdAvailable(false);
  setContentView(editor);
  editor.requestFocus();
 }
 @Override public void onWindowFocusChanged(boolean focused) {
  super.onWindowFocusChanged(focused);
  if(focused) editor.post(() -> { editor.requestFocus(); ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(editor, InputMethodManager.SHOW_FORCED); });
 }
}
