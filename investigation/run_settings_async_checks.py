from pathlib import Path
import subprocess,sys,time
import os
adb=os.environ.get('ADB', 'adb')
serial=os.environ.get('ANDROID_SERIAL', 'emulator-5580')
root=Path(__file__).resolve().parents[1]
classes=','.join([
'com.kazumaproject.markdownhelperkeyboard.setting_activity.SettingsAsyncLoadingInstrumentedTest',
'com.kazumaproject.markdownhelperkeyboard.setting_activity.SettingsNavigationLayoutInstrumentedTest',
'com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting.SettingInlinePreferenceClassificationInstrumentedTest',
'com.kazumaproject.markdownhelperkeyboard.local_font.LocalFontRepositoryCancellationDeviceTest',
'com.kazumaproject.markdownhelperkeyboard.local_font.LocalFontConstructionDeviceTest',
])
excluded='com.kazumaproject.markdownhelperkeyboard.setting_activity.SettingsNavigationLayoutInstrumentedTest#candidatePreviewsKeepNavigationHiddenAcrossRecreationAndRestoreItOnReturn'
def run(args,**kwargs): return subprocess.run([adb,'-s',serial]+args,check=True,**kwargs)
# Preserve the original enabled IMEs, including on test failures.
import atexit
original_enabled=run(['shell','settings','get','secure','enabled_input_methods'],stdout=subprocess.PIPE,text=True).stdout
probe_components=[
 'com.kazumaproject.markdownhelperkeyboard'+suffix+'.freezeprobe/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService'
 for suffix in ['.lite','']
]
def cleanup():
 for component in probe_components:
  if component not in original_enabled:
   subprocess.run([adb,'-s',serial,'shell','ime','disable',component],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
atexit.register(cleanup)
for edition in ['lite','full']:
 other='com.kazumaproject.markdownhelperkeyboard'+('' if edition=='lite' else '.lite')+'.freezeprobe.test'
 subprocess.run([adb,'-s',serial,'uninstall',other],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
 apk=root/f'app/build/intermediates/apk/{edition}Standard/debug/app-{edition}-standard-debug.apk'
 test=root/f'app/build/intermediates/apk/androidTest/{edition}Standard/debug/app-{edition}-standard-debug-androidTest.apk'
 pkg='com.kazumaproject.markdownhelperkeyboard'+('.lite' if edition=='lite' else '')+'.freezeprobe'
 run(['install','-r','-t',str(apk)])
 run(['install','-r','-t',str(test)])
 run(['shell','ime','enable',pkg+'/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService'])
 print(f'RUNNING {edition} on {serial}',flush=True)
 with open(f'/tmp/settings-loading-device-{edition}-final.log','w') as f:
  try:
   run(['shell','am','instrument','-w','-r','-e','class',classes,'-e','notClass',excluded,pkg+'.test/androidx.test.runner.AndroidJUnitRunner'],stdout=f,stderr=subprocess.STDOUT,timeout=300)
  except subprocess.TimeoutExpired:
   f.write('\nHOST_TIMEOUT_SECONDS=300\n')
   run(['shell','am','force-stop',pkg])
  finally:
   log=run(['shell','logcat','-d','-v','brief','-s','SettingsFontConstruction'],stdout=subprocess.PIPE,text=True).stdout
   Path(f'/tmp/settings-loading-constructor-{edition}-final.log').write_text(log)
 result=Path(f'/tmp/settings-loading-device-{edition}-final.log').read_text()
 print('\n'.join(result.splitlines()[-12:]),flush=True)
 if 'OK (' not in result or 'FAILURES!!!' in result or 'HOST_TIMEOUT' in result or 'INSTRUMENTATION_FAILED' in result:
  print(f'Stopping after {edition} failed',flush=True);sys.exit(1)
