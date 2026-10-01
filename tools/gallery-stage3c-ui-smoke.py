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
    for attempt in range(12):
        current = nodes()
        matches = [n for n in current if text in (n.get('text'), n.get('content-desc'))]
        if matches:
            return matches[0]
        if direction and attempt in (1, 3, 5, 7, 9):
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
    for _ in range(6):
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
# Returning from a pair resets the results list to its header. Categories are below it.
# Comparison actions: manual selection, explicit confirmation, system Trash and restore.
tap('Exact duplicates (1)', 'down')
tap('Compare side by side')
wait_text('0 selected · Nothing is selected automatically.', 'down')
tap('Select FileMateCompare_v1.png', 'up')
tap('Assign selected to project')
tap('Comparison fixture project')
tap('Cancel')
wait_text('1 selected · Nothing is selected automatically.', 'down')
tap('Assign selected to project')
tap('Confirm assignment')  # Still no chosen destination: no effect.
wait_text('Assign 1 items')
tap('Comparison fixture project')
tap('Confirm assignment')
wait_text('1 project assignments saved. Images stay in their original locations.', 'down')
tap('Select FileMateCompare_v1.png', 'up')
tap('Move selected to Trash')
wait_text('Move 1 images to Trash?')
tap('Cancel')
wait_text('1 selected · Nothing is selected automatically.', 'up')
tap('Move selected to Trash')
tap('Confirm Trash')

def system_button(positive):
    wanted = 'android:id/button1' if positive else 'android:id/button2'
    for _ in range(12):
        current = nodes()
        matches = [n for n in current if n.get('resource-id') == wanted and n.get('package') != 'app.filemate']
        if matches:
            n = matches[0]
            x1,y1,x2,y2 = map(int,re.findall(r'\d+',n.get('bounds')))
            adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))
            return
        time.sleep(.5)
    raise AssertionError('Android system confirmation button missing: '+wanted)

system_button(False)
wait_text('Android confirmation cancelled. Scan again to continue reviewing.', 'down')
tap('Scan images', 'up')
wait_text('Exact duplicates (1)', 'down')
tap('Compare side by side')
tap('Select FileMateCompare_v1.png')
tap('Move selected to Trash')
tap('Confirm Trash')
system_button(True)
wait_text('1 of 1 images verified in Trash. Open Comparison Trash to restore. Scan again to update comparisons.', 'down')
assert subprocess.call(['adb','shell','test','-f',fixtures[0]]) != 0, 'Trashed item stayed at original path'
# Restart while the image is still trashed: recovery must survive process death.
adb('shell','am','force-stop','app.filemate')
adb('shell','am','start','-n','app.filemate/.MainActivity')
tap('Phone')
tap('Gallery')
tap('Compare images')
tap('Comparison Trash')
tap('FileMateCompare_v1.png')
tap('Restore selected (1)')
tap('Cancel')
wait_text('Restore selected (1)')
tap('Restore selected (1)')
tap('Confirm restore')
system_button(True)
wait_text('1 of 1 images verified restored. Scan again to compare them.', 'up')
# Reopening after a process restart still sees saved project metadata.
adb('shell','am','force-stop','app.filemate')
adb('shell','am','start','-n','app.filemate/.MainActivity')
assert adb('shell','sha256sum',*fixtures) == before, 'Comparison UI changed fixture bytes'
print(json.dumps({'stage3c_ui':'passed','exact_similar_versions':True,'keep_all_rescan_restore':True,'trash_restore_hashes_unchanged':True,'confirmed_assignment':True,'trash_cancel_and_restore':True}))
