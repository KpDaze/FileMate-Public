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
# Choosing a destination and cancelling must not save even a single assignment.
tap('Screenshot_FileMateFixture.png')
tap('Assign to project')
tap('Gallery fixture project')
tap('Cancel')
wait_text('3 visible')
tap('Select photos')
tap('Screenshot_FileMateFixture.png')
tap('FileMateFixture_camera.png')
# Return to the selection toolbar if a small screen scrolled the grid.
adb('shell','input','swipe','500','450','500','1500','400')
wait_text('2 selected')
tap('Assign selected')
tap('Gallery fixture project')
tap('Cancel')
wait_text('3 visible')
wait_text('2 selected')
tap('Assign selected')
tap('Gallery fixture project')
tap('Confirm assignment')
wait_text('2 assigned to Gallery fixture project. Your media stays where it is.')
wait_text('1 visible')
assert not any(n.get('text') == 'Confirm assignment' for n in nodes()), 'Review stayed open'
tap('Refresh')
wait_text('1 visible')
tap('All Gallery')
wait_text('4 visible')
tap('Screenshot_FileMateFixture.png')
wait_text("Project: Gallery fixture project\nConfirmed. Assignment changes only FileMate's records.",scroll=True)
tap('Assign to project')
tap('Clear project assignment')
tap('Confirm assignment')
wait_text('Assignment cleared. Find these items in Unassigned.')
# Details can be opened after scrolling the grid; return to its filter header.
width,height = map(int,re.findall(r'(\d+)x(\d+)',adb('shell','wm','size'))[-1])
adb('shell','input','swipe',str(width//2),str(height*3//10),str(width//2),str(height*8//10),'400')
tap('Unassigned')
wait_text('2 visible')
assert adb('shell','sha256sum',*fixtures) == before, 'Gallery assignment changed fixture bytes or paths'
print('{"passed":true,"native_ui":"single and batch cancel unchanged, confirmed two-item assignment, refresh persistence, All Gallery retention, confirmed clear, unchanged fixture bytes"}')
