import os,sys,json,time,re,subprocess,sqlite3,hashlib,xml.etree.ElementTree as ET
from pathlib import Path
root=Path('/workspace/scratch/386b947316dc');base=root/'android-test';out=base/'evidence/api30-final-inactivity';out.mkdir(parents=True,exist_ok=True)
env=os.environ.copy();env.update(ANDROID_AVD_HOME=str(base/'avd'),ANDROID_EMULATOR_HOME=str(base/'emulator-home-final'),ANDROID_SDK_ROOT=str(base/'sdk'),ANDROID_USER_HOME=str(base/'android-user-35'),TMPDIR=str(base/'tmp'))
adb=str(base/'sdk/platform-tools/adb.original');events=[];emu=None

def record(kind,**data):
 d={'kind':kind,'hostUnixSeconds':time.time(),**data};events.append(d)
 with (out/'events.jsonl').open('a') as f:f.write(json.dumps(d)+'\n')
 print(json.dumps(d),flush=True)

def run(args,timeout=45,required=True,save=None):
 request=base/'api30-final-diagnostic-request.json'
 if request.exists():
  jobs=json.loads(request.read_text());request.unlink();answers=[]
  for job in jobs:
   try:
    check=subprocess.run([adb,*job],env=env,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=7)
    answers.append({'args':job,'code':check.returncode,'output':check.stdout.decode(errors='replace')})
   except subprocess.TimeoutExpired:answers.append({'args':job,'error':'timeout'})
  (out/'diagnostic-response.json').write_text(json.dumps(answers,indent=2))
 try:r=subprocess.run([adb,*args],env=env,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=timeout)
 except subprocess.TimeoutExpired:
  if required:raise RuntimeError('adb timeout: '+str(args[:3]))
  return b''
 if save:(out/save).write_bytes(r.stdout)
 if required and r.returncode:raise RuntimeError('adb failed: '+str(args[:3])+' '+r.stderr.decode(errors='replace')[:500])
 return r.stdout

def snapshot(tag):
 data=run(['exec-out','run-as','app.filemate','cat','databases/filemate.db'],save=tag+'.db')
 if not data.startswith(b'SQLite format 3'):raise RuntimeError('No valid database snapshot')
 c=sqlite3.connect('file:'+str(out/(tag+'.db'))+'?mode=ro',uri=True);c.row_factory=sqlite3.Row
 d={t:[dict(x) for x in c.execute('select * from '+t)] for t in ['hub','observations','history','state']};c.close();return d

def ui(tag):
 run(['shell','uiautomator','dump','/sdcard/final-check.xml'],timeout=60)
 data=run(['exec-out','cat','/sdcard/final-check.xml'],save=tag+'.xml');return ET.fromstring(data)

def tap_text(tree,label):
 for node in tree.iter('node'):
  if node.attrib.get('text','').casefold()==label.casefold():
   x1,y1,x2,y2=map(int,re.findall(r'\d+',node.attrib['bounds']))
   run(['shell','input','tap',str((x1+x2)//2),str((y1+y2)//2)]);return
 raise RuntimeError('Visible control missing: '+label)

def top():return run(['shell','dumpsys','activity','activities']).decode(errors='replace')

try:
 log=open(out/'emulator.log','wb')
 cmd=[str(base/'sdk/emulator/emulator'),'-avd','filemate30timeout','-no-window','-no-audio','-no-boot-anim','-no-snapshot','-gpu','swiftshader','-feature','-Vulkan','-accel','off','-memory','1536','-cores','2','-skin','540x960','-no-metrics']
 emu=subprocess.Popen(cmd,env=env,stdin=subprocess.DEVNULL,stdout=log,stderr=subprocess.STDOUT)
 run(['start-server']);started=time.monotonic();last_report=-60;ready=False
 while time.monotonic()-started<1200:
  if emu.poll() is not None:raise RuntimeError('Emulator exited before readiness: '+str(emu.returncode))
  boot=run(['shell','getprop','sys.boot_completed'],timeout=15,required=False).strip()
  if boot==b'1':
   pkg=run(['shell','pm','path','android'],timeout=20,required=False)
   storage=run(['shell','test -d /sdcard/Download && echo shared-storage-ready'],timeout=15,required=False)
   if b'package:' in pkg and b'shared-storage-ready' in storage:ready=True;break
  elapsed=int(time.monotonic()-started)
  if elapsed-last_report>=30:record('booting',elapsedSeconds=elapsed,bootCompleted=boot.decode(errors='replace'));last_report=elapsed
  time.sleep(5)
 if not ready:raise RuntimeError('Android readiness not reached; no acceptance test performed')
 record('ready',elapsedSeconds=round(time.monotonic()-started))
 run(['shell','svc','bluetooth','disable'],timeout=10,required=False)
 for name in ['window_animation_scale','transition_animation_scale','animator_duration_scale']:run(['shell','settings','put','global',name,'0'],timeout=10,required=False)
 run(['shell','cmd','window','dismiss-keyguard'],timeout=15,required=False)
 existing=run(['shell','pm','path','app.filemate'],required=False)
 if b'package:' in existing:run(['uninstall','app.filemate'],timeout=90)
 record('installing-filemate')
 run(['install','-r',str(base/'FileMate-Test-01-Corrected.apk')],timeout=120,save='install.txt')
 record('installing-fixtures')
 for name in ['qwen','chatgpt']:
  if b'package:' not in run(['shell','pm','path','app.filemate.test.'+name],required=False):run(['install',str(base/'fixtures'/(name+'.apk'))],timeout=90)
 record('preparing-test-permissions-and-hub')
 run(['push',str(base/'runtime-seed.db'),'/data/local/tmp/filemate-seed.db'])
 for args in [['shell','appops','set','--uid','app.filemate','MANAGE_EXTERNAL_STORAGE','allow'],['shell','appops','set','app.filemate','GET_USAGE_STATS','allow'],['shell','run-as','app.filemate','mkdir','-p','databases'],['shell','run-as','app.filemate','cp','/data/local/tmp/filemate-seed.db','databases/filemate.db']]:run(args)
 path=run(['shell','pm','path','app.filemate']).decode().strip().removeprefix('package:')
 installed=run(['shell','sha256sum',path]).decode();expected=hashlib.sha256((base/'FileMate-Test-01-Corrected.apk').read_bytes()).hexdigest()
 if not installed.startswith(expected):raise RuntimeError('Installed package hash mismatch')
 record('installed',sha256=expected)
 run(['shell','am','start','-W','-n','app.filemate/.MainActivity'],timeout=90,required=False)
 found=False
 for attempt in range(12):
  tree=ui('hub-'+str(attempt));texts=[n.attrib.get('text','') for n in tree.iter('node')]
  if any(("System UI isn't responding" in x or "Quickstep isn't responding" in x) for x in texts):
   record('android-dialog',texts=texts)
   for n in tree.iter('node'):
    if n.attrib.get('text')=='Wait':
     x1,y1,x2,y2=map(int,re.findall(r'\d+',n.attrib['bounds']));run(['shell','input','tap',str((x1+x2)//2),str((y1+y2)//2)])
   time.sleep(5);continue
  if 'ChatGPT Test' in texts and 'AI Hub' in texts:found=True;break
  run(['shell','am','start','-W','-n','app.filemate/.MainActivity'],timeout=60,required=False);time.sleep(5)
 if not found:raise RuntimeError('Hub not ready; see UI evidence')
 run(['exec-out','screencap','-p'],save='hub.png')
 tap_text(tree,'ChatGPT Test')
 launched=False
 for attempt in range(12):
  time.sleep(5);s=top();(out/'launch-activities.txt').write_text(s)
  if re.search(r'topResumedActivity=.*app\.filemate\.test\.chatgpt/',s):launched=True;break
 if not launched:raise RuntimeError('ChatGPT fixture did not launch from Hub')
 tree=ui('fixture-opened')
 if not any(n.attrib.get('text')=='ChatGPT TEST EXPORTER' for n in tree.iter('node')):raise RuntimeError('Fixture UI not confirmed')
 record('hub-launch-passed')
 tap_text(tree,'Save AI file')
 live_deadline=time.monotonic()+120
 while True:
  time.sleep(5);first=snapshot('before-home')
  if any(x['via']=='Live monitoring' and x['source']=='ChatGPT' for x in first['observations']):break
  if time.monotonic()>live_deadline:raise RuntimeError('No live fixture export before inactivity test within 120 seconds')
 run(['shell','input','keyevent','3'])
 start_raw=run(['shell','date +%s; cat /proc/uptime; cmd notification list'],save='inactivity-start.txt').decode().splitlines()
 start_wall=int(start_raw[0]);start_uptime=float(start_raw[1].split()[0]);record('inactivity-started',deviceUnixSeconds=start_wall,uptimeSeconds=start_uptime)
 result=None
 for count in range(66):
  raw=run(['shell','date +%s; cat /proc/uptime; cmd notification list'],save='probe-'+str(count)+'.txt').decode().splitlines()
  d=snapshot('probe-'+str(count));state={x['key']:x['value'] for x in d['state']};auto=[x for x in d['history'] if x['title']=='Monitoring ended automatically'];uptime=float(raw[1].split()[0]);elapsed=uptime-start_uptime;notification=any('|app.filemate|44|' in x for x in raw)
  record('inactivity-probe',elapsedSeconds=round(elapsed,2),sessionActive=state.get('session_active'),notificationPresent=notification,automaticStop=auto[-1:] if auto else [],observations=len(d['observations']))
  if state.get('session_active')=='false':
   if not auto or notification or elapsed<1799:raise RuntimeError('Session ended without verified full automatic timeout')
   result={'status':'passed','elapsedSeconds':elapsed,'startDeviceUnixSeconds':start_wall,'endDeviceUnixSeconds':int(raw[0]),'automaticStop':auto[-1],'sessionActive':False,'notificationPresent':False,'database':d,'candidateSha256':expected,'scope':'Android 11 emulator and synthetic local fixtures; physical phone not yet tested','setup':'adb-granted permissions and prepared two-app Hub; no clock changes or shortened timeout'};break
  if elapsed>1890:raise RuntimeError('Monitoring still active after timeout allowance')
  time.sleep(30)
 if result is None:raise RuntimeError('No automatic-stop result')
 run(['shell','dumpsys','activity','exit-info','app.filemate'],save='app-exit-info.txt')
 run(['logcat','-d','-b','crash'],save='crash-log.txt')
 (out/'result.json').write_text(json.dumps(result,indent=2)+'\n');record('passed',elapsedSeconds=result['elapsedSeconds'])
except Exception as e:
 (out/'result.json').write_text(json.dumps({'status':'incomplete','error':str(e),'lastEvent':events[-1] if events else None},indent=2)+'\n');record('incomplete',error=str(e))
finally:
 if emu is not None:
  run(['logcat','-d','-t','1500'],timeout=20,required=False,save='logcat-final.txt');run(['emu','kill'],timeout=10,required=False)
  try:emu.wait(timeout=15)
  except subprocess.TimeoutExpired:emu.terminate()

