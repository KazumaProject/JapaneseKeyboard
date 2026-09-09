#!/usr/bin/env python3
"""Select a layout in the isolated test APK only. Never writes the user's IME data."""
import argparse
import json
import shlex
import subprocess
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument('--serial', required=True)
parser.add_argument('--keyboard', choices=['TENKEY', 'QWERTY', 'ROMAJI', 'SUMIRE', 'GOJUON'], required=True)
parser.add_argument('--floating', action='store_true')
parser.add_argument('--baseline', action='store_true')
parser.add_argument('--bunsetsu-separation', choices=['on', 'off'])
parser.add_argument('--type-null-mode', choices=['default', 'direct_commit', 'composing_text'])
args = parser.parse_args()
pkg = 'com.kazumaproject.markdownhelperkeyboard.lite.' + ('enterbaseline' if args.baseline else 'enterparity')
adb = ['adb', '-s', args.serial]
path = f'shared_prefs/{pkg}_preferences.xml'
subprocess.run(adb + ['shell', 'am', 'force-stop', pkg], check=True)
old = subprocess.run(adb + ['shell', 'run-as', pkg, 'cat', path], capture_output=True, text=True)
root = ET.fromstring(old.stdout) if old.returncode == 0 else ET.Element('map')
values = {
    'keyboard_order_preference': ('string', json.dumps([args.keyboard, 'QWERTY'] if args.keyboard != 'QWERTY' else ['QWERTY', 'TENKEY'])),
    'save_last_used_keyboard': ('boolean', 'false'),
    'save_last_used_keyboard_int': ('int', '0'),
    'keyboard_floating_preference': ('boolean', str(args.floating).lower()),
}
if args.type_null_mode is not None:
    values['type_null_input_behavior_preference'] = ('string', args.type_null_mode)
if args.bunsetsu_separation is not None:
    values['conversion_bunsetsu_separation_preference'] = ('boolean', str(args.bunsetsu_separation == 'on').lower())
for name, (kind, value) in values.items():
    for child in list(root):
        if child.get('name') == name:
            root.remove(child)
    child = ET.SubElement(root, kind, name=name)
    if kind == 'string': child.text = value
    else: child.set('value', value)
xml = ET.tostring(root, encoding='utf-8', xml_declaration=True)
subprocess.run(adb + ['shell', 'run-as', pkg, 'mkdir', '-p', 'shared_prefs'], check=True)
command = shlex.quote('cat > ' + shlex.quote(path))
subprocess.run(adb + ['shell', 'run-as', pkg, 'sh', '-c', command], input=xml, check=True)
print(json.dumps(values))
