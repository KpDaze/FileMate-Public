"""Stage 3D UI proof for one explicitly approved Gallery emulator run."""
import json,re,subprocess,sys,time,xml.etree.ElementTree as ET

def adb(*args): return subprocess.check_output(['adb',*args],text=True)
width,height=map(int,re.findall(r'(\d+)x(\d+)',adb('shell','wm','size'))[-1])
def nodes():
    adb('shell','uiautomator','dump','/sdcard/filemate-stage3d-ui.xml')
    return list(ET.fromstring(adb('shell','cat','/sdcard/filemate-stage3d-ui.xml')).iter('node'))
def report_failure(kind,error,traceback):
    try:
        print('STAGE3D_FAILURE_UI',ET.tostring(ET.fromstring(adb('shell','cat','/sdcard/filemate-stage3d-ui.xml')),encoding='unicode'),flush=True)
    except Exception as dump_error: print('Could not read last UI dump:',dump_error,flush=True)
    sys.__excepthook__(kind,error,traceback)
sys.excepthook=report_failure
def scroll(direction):
    start,end=(.74,.37) if direction=='down' else (.36,.78)
    adb('shell','input','swipe',str(width//2),str(int(height*start)),str(width//2),str(int(height*end)),'350')
def wait(text,direction=None):
    cur=[]
    for n in range(14):
        cur=nodes(); hit=[x for x in cur if text in (x.get('text'),x.get('content-desc'))]
        if hit:return hit[0]
        if direction and n in (1,3,5,7,9,11):scroll(direction)
        time.sleep(.35)
    raise AssertionError(f'Missing {text!r}; visible={[x.get("text") for x in cur if x.get("text")]}')
def tap(text,direction='down'):
    print('Stage 3D:',text,flush=True);n=wait(text,direction)
    x1,y1,x2,y2=map(int,re.findall(r'\d+',n.get('bounds')))
    adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))
def replace_text(value):
    adb('shell','input','keyevent','KEYCODE_MOVE_END')
    for _ in range(100): adb('shell','input','keyevent','KEYCODE_DEL')
    adb('shell','input','text',value.replace(' ','%s'))

fixtures=['/sdcard/Download/'+n for n in ('FileMateCompare_v1.png','FileMateCompare_v2.png','FileMateCompare_copy.png')]
before=adb('shell','sha256sum',*fixtures)
adb('shell','am','start','-n','app.filemate/.MainActivity')
tap('Phone');tap('Gallery');tap('All Gallery')
tap('Select photos')
tap('FileMateCompare_v1.png');tap('Favourite')
tap('Favourites','up');wait('★ Favourite','down')
tap('Select photos','up');tap('FileMateCompare_v1.png');tap('Unfavourite')
wait('0 visible','up')
tap('Albums');tap('Create album')
replace_text('UI Album');tap('Create')
wait('UI Album (0)')
tap('All Gallery','up');tap('Select photos');tap('FileMateCompare_v1.png');tap('FileMateCompare_copy.png')
tap('Add to album');tap('UI Album (0)')
tap('Albums','up');tap('UI Album (2)')
wait('2 visible','up')
tap('Select photos');tap('FileMateCompare_copy.png');tap('Remove from album')
wait('1 visible','up')
tap('Rename album');replace_text('Renamed');tap('Rename')
wait('Renamed (1)')
tap('Delete album');wait('This removes only the FileMate album.',None);tap('Delete album')
wait('Create album')
assert adb('shell','sha256sum',*fixtures)==before,'Stage 3D UI changed fixture bytes'
print(json.dumps({'stage3d_ui':'passed','favourite_unfavourite':True,'album_create_add_remove_rename_delete':True,'hashes_unchanged':True}))
