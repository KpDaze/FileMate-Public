"""Bounded taps through the native Gallery; no filesystem-changing UI actions."""
import re, subprocess, time, xml.etree.ElementTree as ET

def adb(*args):
    return subprocess.check_output(['adb',*args],text=True)

def nodes():
    adb('shell','uiautomator','dump','/sdcard/filemate-gallery-ui.xml')
    return list(ET.fromstring(adb('shell','cat','/sdcard/filemate-gallery-ui.xml')).iter('node'))

def wait_text(text):
    for attempt in range(8):
        current = nodes()
        matched = [n for n in current if n.get('text') == text or n.get('content-desc') == text]
        if matched:
            return matched[0]
        time.sleep(.5)
    raise AssertionError(f'Missing {text!r}; visible: {[n.get("text") for n in current if n.get("text")]}')

def tap(text):
    n = wait_text(text)
    x1,y1,x2,y2 = map(int,re.findall(r'\d+',n.get('bounds')))
    adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))

adb('shell','am','start','-n','app.filemate/.MainActivity')
tap('Phone')
tap('Gallery')
wait_text('4 visible')
tap('Screenshots')
wait_text('1 visible')
tap('Screenshot_FileMateFixture.png')
wait_text('Done')
tap('Done')
tap('Camera')
wait_text('1 visible')
tap('All')
wait_text('4 visible')
print('{"passed":true,"native_ui":"Gallery entry, filters and details"}')
