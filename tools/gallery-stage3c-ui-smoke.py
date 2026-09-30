"""Stage 3C UI proof in the already-running, explicitly approved emulator.
Only shell-owned disposable fixtures; the UI assigns metadata, never media bytes.
"""
import json
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET


def adb(*args):
    return subprocess.check_output(['adb', *args], text=True)


width, height = map(int, re.findall(r'(\d+)x(\d+)', adb('shell', 'wm', 'size'))[-1])


def nodes():
    adb('shell', 'uiautomator', 'dump', '/sdcard/filemate-stage3c-ui.xml')
    return list(ET.fromstring(adb('shell', 'cat', '/sdcard/filemate-stage3c-ui.xml')).iter('node'))


def report_failure(kind, error, traceback):
    # Preserve the exact UI evidence if a later assertion fails.
    try:
        print('STAGE3C_FAILURE_UI', ET.tostring(ET.fromstring(adb('shell', 'cat', '/sdcard/filemate-stage3c-ui.xml')), encoding='unicode'), flush=True)
    except Exception as dump_error:
        print('Could not read last UI dump:', dump_error, flush=True)
    sys.__excepthook__(kind, error, traceback)


sys.excepthook = report_failure


def scroll(direction):
    start, end = (.74, .37) if direction == 'down' else (.36, .78)
    adb('shell', 'input', 'swipe', str(width // 2), str(int(height * start)),
        str(width // 2), str(int(height * end)), '350')


def wait_text(text, direction=None):
    current = []
    for attempt in range(8):
        current = nodes()
        matches = [n for n in current if text in (n.get('text'), n.get('content-desc'))]
        if matches:
            return matches[0]
        if direction and attempt in (1, 3, 5):
            scroll(direction)
        time.sleep(.4)
    raise AssertionError(f'Missing {text!r}; visible: {[n.get("text") for n in current if n.get("text")]}')


def tap(text, direction='down'):
    print(f'Stage 3C: {text}', flush=True)
    n = wait_text(text, direction)
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', n.get('bounds')))
    adb('shell', 'input', 'tap', str((x1 + x2) // 2), str((y1 + y2) // 2))


def top():
    # Bounded return to the header, using the actual device size.
    for _ in range(3):
        scroll('up')


fixtures = ['/sdcard/Download/' + name for name in ('FileMateCompare_v1.png','FileMateCompare_v2.png','FileMateCompare_copy.png')]
before = adb('shell','sha256sum',*fixtures)
adb('shell','am','start','-n','app.filemate/.MainActivity')
tap('Phone')
tap('Gallery')
tap('Compare images')
tap('Scan images')
wait_text('Exact duplicates (1)', 'down')
tap('Compare side by side')
wait_text('Exact duplicates', 'up')
tap('View larger')
tap('Done')
tap('Back without deciding')
wait_text('Exact duplicates (1)', 'down')
tap('Compare side by side')
tap('Keep all 2 images')
wait_text('Exact duplicates (0)', 'down')
top()
tap('Scan again')
wait_text('Exact duplicates (0)', 'down')
tap('Show kept groups')
wait_text('Exact duplicates (1)')
tap('Compare side by side')
tap('Review this group again')
tap('Hide kept groups')
wait_text('Exact duplicates (1)')
tap('Similar images (1)', 'up')
tap('Compare side by side')
wait_text('Similar images', 'up')
tap('Back without deciding')
tap('Possible versions (1)', 'down')
tap('Compare side by side')
wait_text('Possible versions', 'up')
tap('Back without deciding')
assert adb('shell','sha256sum',*fixtures) == before, 'Comparison UI changed fixture bytes'
print(json.dumps({'stage3c_ui':'passed','exact_similar_versions':True,'keep_all_rescan_restore':True,'hashes_unchanged':True}))
