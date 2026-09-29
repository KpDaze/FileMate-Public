"""Stage 3B UI proof in the already-running, explicitly approved emulator.
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
    adb('shell', 'uiautomator', 'dump', '/sdcard/filemate-stage3b-ui.xml')
    return list(ET.fromstring(adb('shell', 'cat', '/sdcard/filemate-stage3b-ui.xml')).iter('node'))


def report_failure(kind, error, traceback):
    # Preserve the exact UI evidence if a later assertion fails.
    try:
        print('STAGE3B_FAILURE_UI', ET.tostring(ET.fromstring(adb('shell', 'cat', '/sdcard/filemate-stage3b-ui.xml')), encoding='unicode'), flush=True)
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
    print(f'Stage 3B: {text}', flush=True)
    n = wait_text(text, direction)
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', n.get('bounds')))
    adb('shell', 'input', 'tap', str((x1 + x2) // 2), str((y1 + y2) // 2))


def top():
    # Bounded return to the header, using the actual device size.
    for _ in range(3):
        scroll('up')


fixtures = [
    '/sdcard/Pictures/Screenshots/Screenshot_FileMateFixture.png',
    '/sdcard/Pictures/Screenshots/Screenshot_FileMateFixture_second.png',
    '/sdcard/Download/Screenshot_FileMateFixture_download.png',
    '/sdcard/DCIM/Camera/FileMateFixture_camera.png',
    '/sdcard/Download/FileMateFixture_download.png',
    '/sdcard/Movies/FileMateFixture_video.mp4',
]
before = adb('shell', 'sha256sum', *fixtures)
adb('shell', 'am', 'start', '-n', 'app.filemate/.MainActivity')
tap('Projects')
tap('Needs Sorting')
wait_text('2 screenshots · Pictures/Screenshots', 'down')
wait_text('1 screenshots · Download', 'down')
wait_text('No other files waiting.', 'down')
# The overlap fixture exists in the organiser; it must not also render as a file row.
assert not any(n.get('text') == 'Screenshot_FileMateFixture.png' for n in nodes()), 'Screenshot duplicated under Other files'
top()
tap('2 screenshots · Pictures/Screenshots')
wait_text('2 visible')
wait_text('Pictures/Screenshots · 2 items', 'down')
assert not any(n.get('text') in ('FileMateFixture_camera.png', 'FileMateFixture_video.mp4', 'Screenshot_FileMateFixture_download.png') for n in nodes())
tap('Select photos', 'up')
tap('Screenshot_FileMateFixture.png')
tap('Screenshot_FileMateFixture_second.png')
tap('Assign selected', 'up')
tap('Gallery fixture project')
tap('Cancel')
wait_text('2 selected', 'up')
wait_text('2 visible', 'up')
tap('Assign selected')
# UIAutomator may expose the Text label separately from its disabled Button.
# Test the actual behavior instead of treating the label's enabled flag as the button.
wait_text('Confirm assignment')
assert not any((n.get('text') or '').startswith('Selected:') for n in nodes()), 'Destination survived cancellation'
tap('Confirm assignment')  # No destination chosen: this must have no effect.
wait_text('Assign 2 items')
wait_text('Choose a project, then confirm. Nothing changes until you confirm.')
assert not any((n.get('text') or '').startswith('Selected:') for n in nodes()), 'Confirm invented a destination'
tap('Gallery fixture project')
tap('Confirm assignment')
wait_text('2 assigned to Gallery fixture project. Your media stays where it is.', 'down')
wait_text('0 visible', 'up')
tap('Needs Sorting', 'up')
wait_text('1 screenshots · Download', 'down')
# Opening all groups must now show only the one remaining unassigned screenshot.
tap('Review all unassigned screenshots')
wait_text('1 visible')
# This entry opens at the page top. Refresh and the filter row are below the
# access card on the 320x640 emulator, so search down rather than above it.
tap('Refresh', 'down')
wait_text('Refresh')  # The label returns after the scan finishes.
wait_text('1 visible', 'up')
tap('All Gallery', 'down')
wait_text('6 visible', 'up')
tap('Screenshot_FileMateFixture.png')
wait_text("Project: Gallery fixture project\nConfirmed. Assignment changes only FileMate's records.", 'down')
tap('Assign to project')
tap('Clear project assignment')
tap('Confirm assignment')
wait_text('Assignment cleared. Find these items in Unassigned.', 'down')
top()
tap('Needs Sorting', 'up')
wait_text('1 screenshots · Pictures/Screenshots', 'down')
wait_text('1 screenshots · Download', 'down')
assert adb('shell', 'sha256sum', *fixtures) == before, 'Stage 3B UI changed fixture bytes or paths'
print(json.dumps({'passed': True, 'stage3b_native_ui': 'date/folder groups, duplicate-row suppression, camera/video exclusion, cancel unchanged, reset destination, confirmed batch, intake removal, refresh persistence, All Gallery retention, clear restores intake, six original paths and hashes unchanged'}))
