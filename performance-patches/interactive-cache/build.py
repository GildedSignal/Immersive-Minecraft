from pathlib import Path
import subprocess,zipfile,json,hashlib,argparse
R=Path(__file__).resolve().parent
J=Path.home()/'AppData/Local/Packages/Microsoft.4297127D64EC6_8wekyb3d8bbwe/LocalCache/Local/runtime/java-runtime-epsilon/windows-x64/java-runtime-epsilon/bin'
G=Path.home()/'AppData/Roaming/ModrinthApp/profiles/Fabric 26.2/mods'
parser=argparse.ArgumentParser();parser.add_argument('--original',type=Path);parser.add_argument('--jdk',type=Path,default=J);parser.add_argument('--asm',type=Path,default=Path.home()/'AppData/Roaming/ModrinthApp/meta/libraries/org/ow2/asm/asm/9.10.1/asm-9.10.1.jar');args=parser.parse_args();J=args.jdk
original=args.original or next(G.glob('interactivestuff-0.7.0-26.2.jar'));asm=args.asm
out=R/'build';out.mkdir(exist_ok=True)
def run(*args):
 p=subprocess.run(list(map(str,args)),capture_output=True,text=True,timeout=90)
 if p.returncode:raise RuntimeError(p.stdout+p.stderr)
 return p.stdout.strip()
print(run(J/'javac.exe','-proc:none','-cp',str(original)+';'+str(asm),'-d',out,*[R/f for f in ['mcperf/interactive/NativeMetadata.java','NativeBinderPatch.java','NativeBinderCacheTest.java','NativeCacheAgent.java']]))
target='me/abdelaziz/util/NativeBinder.class'
with zipfile.ZipFile(original) as z:(out/'NativeBinder-original.class').write_bytes(z.read(target))
run(J/'java.exe','-cp',str(out)+';'+str(asm),'NativeBinderPatch',out/'NativeBinder-original.class',out/'NativeBinder-patched.class')
patched=R/'interactivestuff-0.7.0-26.2-metadata-cache.jar'
helpers=list((out/'mcperf/interactive').glob('*.class'))
with zipfile.ZipFile(original) as source,zipfile.ZipFile(patched,'w',zipfile.ZIP_DEFLATED) as destination:
 for entry in source.infolist():destination.writestr(entry,(out/'NativeBinder-patched.class').read_bytes() if entry.filename==target else source.read(entry))
 for file in helpers:destination.write(file,file.relative_to(out).as_posix())
with zipfile.ZipFile(original) as source,zipfile.ZipFile(patched) as result:
 assert all(source.read(n)==result.read(n) for n in source.namelist() if n!=target)
logs=[]
for name,jar in [('original',original),('cached',patched)]:
 text=run(J/'java.exe','-Xverify:all','-cp',str(out)+';'+str(jar),'NativeBinderCacheTest');logs.append(name+': '+text);print(logs[-1])
(R/'validation.txt').write_text('\n'.join(logs)+'\n',encoding='utf-8')
with zipfile.ZipFile(R/'native-metadata-helper.jar','w',zipfile.ZIP_DEFLATED) as z:
 for f in helpers:z.write(f,f.relative_to(out).as_posix())
with zipfile.ZipFile(R/'native-cache-agent.jar','w',zipfile.ZIP_DEFLATED) as z:
 z.writestr('META-INF/MANIFEST.MF','Manifest-Version: 1.0\nAgent-Class: NativeCacheAgent\nMain-Class: NativeCacheAgent\nCan-Retransform-Classes: true\n\n')
 for file in out.glob('NativeCacheAgent*.class'):z.write(file,file.name)
 for file in out.glob('NativeBinderPatch*.class'):z.write(file,file.name)
 with zipfile.ZipFile(asm) as source:
  for entry in source.infolist():
   if entry.filename.startswith('org/objectweb/asm/') and not entry.is_dir():z.writestr(entry,source.read(entry))
(R/'build-result.json').write_text(json.dumps({'originalSha256':hashlib.sha256(original.read_bytes()).hexdigest(),'patchedSha256':hashlib.sha256(patched.read_bytes()).hexdigest(),'modifiedEntries':[target],'addedEntries':[f.relative_to(out).as_posix() for f in helpers],'semanticAssertionsPerVersion':110,'scope':'Two immutable reflection metadata enumerations; no hosts/environments/animation values cached'},indent=2)+'\n',encoding='utf-8')
print('Staged cache patch; original installed JAR unchanged.')
