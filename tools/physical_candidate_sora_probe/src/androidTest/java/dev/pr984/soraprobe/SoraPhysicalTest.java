package dev.pr984.soraprobe;
import android.app.Instrumentation;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.BeforeClass;
import org.junit.AfterClass;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import java.util.function.BooleanSupplier;
@RunWith(AndroidJUnit4.class)
public class SoraPhysicalTest {
 private static final Instrumentation inst = InstrumentationRegistry.getInstrumentation();
 private ActivityScenario<HostActivity> host;
 private int device;
 private static String shell(String command) throws Exception {
  try(java.io.InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(inst.getUiAutomation().executeShellCommand(command))) {
   return new String(in.readAllBytes()).trim();
  }
 }
 private static String previous;
 private static String showWithHardware;
 @BeforeClass public static void connectIme() throws Exception {
  previous=shell("settings get secure default_input_method");
  showWithHardware=shell("settings get secure show_ime_with_hard_keyboard");
  String ime="com.kazumaproject.markdownhelperkeyboard.lite/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService";
  shell("settings put secure show_ime_with_hard_keyboard 1");
  shell("ime enable "+ime); shell("ime set "+ime);
 }
 @AfterClass public static void restoreIme() throws Exception {
  shell("ime set "+previous);
  shell(showWithHardware.equals("null") ? "settings delete secure show_ime_with_hard_keyboard" : "settings put secure show_ime_with_hard_keyboard "+showWithHardware);
 }
 private void fixture(Runnable action) throws Exception {
  device=-1;
  for(int id: InputDevice.getDeviceIds()) {
   InputDevice d=InputDevice.getDevice(id);
   if(d!=null && !d.isVirtual() && d.getKeyboardType()==InputDevice.KEYBOARD_TYPE_ALPHABETIC) device=id;
  }
  assertTrue(device>=0);
  try {
   host=ActivityScenario.launch(HostActivity.class);
   long readyBy = SystemClock.uptimeMillis() + 30000;
   boolean ready = false;
   String observed = "";
   while (!ready && SystemClock.uptimeMillis() < readyBy) {
    SystemClock.sleep(500);
    key(KeyEvent.KEYCODE_KANA);
    key(KeyEvent.KEYCODE_A);
    observed = text();
    ready = observed.equals("あ");
    host.onActivity(a -> {
     a.editor.setText("");
     a.editor.restartInput();
     ((android.view.inputmethod.InputMethodManager)a.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)).showSoftInput(a.editor, android.view.inputmethod.InputMethodManager.SHOW_FORCED);
    });
   }
   assertTrue("Japanese IME did not initialize: ["+observed+"]", ready);
   SystemClock.sleep(500);
   action.run();
  } finally {
   if(host!=null) host.close();
  }
 }
 private String text() { String[] s={""}; host.onActivity(a->s[0]=a.editor.getText().toString()); return s[0]; }
 private void await(BooleanSupplier check) { long end=SystemClock.uptimeMillis()+10000; while(SystemClock.uptimeMillis()<end) { if(check.getAsBoolean()) return; SystemClock.sleep(50); } fail("Editor: "+text()); }
 private void key(int code) {
  long time=SystemClock.uptimeMillis();
  for(int action:new int[]{KeyEvent.ACTION_DOWN,KeyEvent.ACTION_UP}) assertTrue(inst.getUiAutomation().injectInputEvent(new KeyEvent(time,SystemClock.uptimeMillis(),action,code,0,0,device,0,0,InputDevice.SOURCE_KEYBOARD),true));
  SystemClock.sleep(160);
 }
 private void type(String s) { for(char c:s.toCharArray()) key(KeyEvent.KEYCODE_A+c-'a'); }
 private void partial() {
  type("ashitaharerutoiidesunehareta");
  await(()->text().equals("あしたはれるといいですねはれた"));
  key(KeyEvent.KEYCODE_SPACE);
  for(int i=0;i<40;i++) {
   if(text().equals("明日はれるといいですねはれた")) return;
   key(KeyEvent.KEYCODE_DPAD_DOWN);
  }
  fail("No partial candidate: "+text());
 }
 @Test public void narrowedTypingPreservesAllText() throws Exception { fixture(()->{partial();String preview=text();type("a");await(()->text().equals(preview+"あ"));}); }
 @Test public void cursorTailAndCancelPreserveAllText() throws Exception { fixture(()->{type("ashitaha");await(()->text().equals("あしたは"));key(KeyEvent.KEYCODE_DPAD_LEFT);key(KeyEvent.KEYCODE_SPACE);await(()->text().equals("明日は"));key(KeyEvent.KEYCODE_ESCAPE);await(()->text().equals("あしたは"));}); }
 @Test public void partialEnterKeepsRemainder() throws Exception { fixture(()->{partial();String preview=text();key(KeyEvent.KEYCODE_ENTER);SystemClock.sleep(700);key(KeyEvent.KEYCODE_ESCAPE);await(()->text().equals(preview));key(KeyEvent.KEYCODE_ENTER);await(()->text().equals(preview));}); }
}
