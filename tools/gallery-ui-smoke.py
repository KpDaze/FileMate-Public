"""Bounded taps through the native Gallery; no filesystem-changing UI actions."""
import re, subprocess, time, xml.etree.ElementTree as ET

def adb(*args):
    return subprocess.check_output(['adb',*args],text=True)

def nodes():
    adb('shell','uiautomator','dump','/sdcard/filemate-gallery-ui.xml')
    return list(ET.fromstring(adb('shell','cat','/sdcard/filemate-gallery-ui.xml')).iter('node'))

def wait_text(text, scroll=False):
    for attempt in range(8):
        current = nodes()
        matched = [n for n in current if n.get('text') == text or n.get('content-desc') == text]
        if matched:
            return matched[0]
        if scroll and attempt in (2,4,6):
            width,height = map(int,re.findall(r'(\d+)x(\d+)',adb('shell','wm','size'))[-1])
            adb('shell','input','swipe',str(width//2),str(height*7//10),str(width//2),str(height*4//10),'400')
        time.sleep(.5)
    raise AssertionError(f'Missing {text!r}; visible: {[n.get("text") for n in current if n.get("text")]}')

def tap(text):
    print(f"Opening {text}",flush=True)
    n = wait_text(text,scroll=True)
    x1,y1,x2,y2 = map(int,re.findall(r'\d+',n.get('bounds')))
    adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))

fixtures = ['/sdcard/Pictures/Screenshots/Screenshot_FileMateFixture.png',
    '/sdcard/DCIM/Camera/FileMateFixture_camera.png',
    '/sdcard/Download/FileMateFixture_download.png', '/sdcard/Movies/FileMateFixture_video.mp4']
before = adb('shell','sha256sum',*fixtures)
adb('shell','am','start','-n','app.filemate/.MainActivity')
tap('Phone')
tap('Gallery')
wait_text('3 visible')
tap('Screenshot_FileMateFixture.png')
tap('Gallery fixture project')
wait_text('Assigned to Gallery fixture project. Your media stays where it is.')
wait_text('2 visible')
assert not any(n.get('text') == 'Done' for n in nodes()), 'Assignment details stayed open'
tap('Refresh')
wait_text('2 visible')
tap('All Gallery')
wait_text('4 visible')
tap('Screenshot_FileMateFixture.png')
wait_text("Project: Gallery fixture project\nConfirmed. Assignment changes only FileMate's records.",scroll=True)
tap('Clear project assignment')
wait_text('Assignment cleared. Find this item in Unassigned.')
tap('Unassigned')
wait_text('3 visible')
assert adb('shell','sha256sum',*fixtures) == before, 'Gallery assignment changed fixture bytes or paths'
print('{"passed":true,"native_ui":"Unassigned removal, saved assignment after refresh, All Gallery retention, clear restores Unassigned, unchanged fixture bytes"}')
