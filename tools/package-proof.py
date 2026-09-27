#!/usr/bin/env python3
"""Remove unused ZIP gaps, align and re-sign with the same private test identity.
Usage: python3 tools/package-proof.py INPUT.apk OUTPUT.apk
Requires ANDROID_HOME, FILEMATE_TEST_KEY and FILEMATE_KEY_PASSWORD.
"""
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import zipfile
source, destination = (Path(p).resolve() for p in sys.argv[1:])
tools = Path(os.environ['ANDROID_HOME']) / 'build-tools' / '35.0.0'
assert source != destination
with tempfile.TemporaryDirectory(prefix='filemate-package-', dir=destination.parent) as work:
    unsigned, aligned = Path(work)/'unsigned.apk', Path(work)/'aligned.apk'
    with zipfile.ZipFile(source) as old, zipfile.ZipFile(unsigned,'w') as new:
        for entry in old.infolist(): new.writestr(entry,old.read(entry.filename))
    subprocess.run([str(tools/'zipalign'),'-P','16','-f','4',str(unsigned),str(aligned)],check=True)
    subprocess.run([str(tools/'apksigner'),'sign','--ks',os.environ['FILEMATE_TEST_KEY'],'--ks-key-alias','filemate-proof','--ks-pass','env:FILEMATE_KEY_PASSWORD','--key-pass','env:FILEMATE_KEY_PASSWORD','--v1-signing-enabled','false','--out',str(destination),str(aligned)],check=True)
    subprocess.run([str(tools/'apksigner'),'verify','--verbose',str(destination)],check=True)
    subprocess.run([str(tools/'zipalign'),'-c','-P','16','4',str(destination)],check=True)
with zipfile.ZipFile(source) as a,zipfile.ZipFile(destination) as b:
    assert a.namelist()==b.namelist()
    assert all(a.read(n)==b.read(n) for n in a.namelist())
print(f'{destination.name}: {destination.stat().st_size:,} bytes; entries unchanged, aligned and signature verified')
