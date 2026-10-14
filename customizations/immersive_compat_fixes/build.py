from pathlib import Path
import subprocess, json, zipfile
R=Path(__file__).resolve().parent; W=R.parents[1]
J=Path.home()/'AppData/Local/Packages/Microsoft.4297127D64EC6_8wekyb3d8bbwe/LocalCache/Local/runtime/java-runtime-epsilon/windows-x64/java-runtime-epsilon/bin'
G=Path.home()/'AppData/Local/ImmersiveMinecraft/data/instances/immersive-minecraft/.minecraft'
M=Path.home()/'AppData/Roaming/ModrinthApp/meta'
cp=(W/'tools/immersive-wiki/reference/compile-classpath.txt').read_text().strip()
cp+=';'+str(M/'libraries/net/fabricmc/fabric-loader/0.19.5/fabric-loader-0.19.5.jar')+';'+';'.join(str(p) for p in (G/'mods').glob('*.jar'))
(R/'classpath.txt').write_text(cp)
out=R/'classes'; out.mkdir(exist_ok=True)
args=['-proc:none','-encoding','UTF-8','-cp',cp,'-d',str(out),*map(str,(R/'src').rglob('*.java'))]
(R/'javac.args').write_text('\n'.join('"'+a.replace('\\','/')+'"' for a in args))
p=subprocess.run([str(J/'javac.exe'),'@'+str(R/'javac.args')],capture_output=True,text=True,encoding='utf-8')
print(p.stdout+p.stderr)
if p.returncode:raise SystemExit(p.returncode)
with zipfile.ZipFile(R/'immersive-compat-fixes-1.1.0+26.2.jar','w',zipfile.ZIP_DEFLATED) as z:
 for f in (out/'mcperf').rglob('*.class'):z.write(f,f.relative_to(out).as_posix())
 z.writestr('fabric.mod.json',json.dumps({'schemaVersion':1,'id':'immersive_compat_fixes','version':'1.1.0+26.2','name':'Immersive Compatibility Fixes','environment':'client','license':'MIT','description':'Main-thread Interactive Stuff callbacks, private local Field Guide discoveries and journals, direct chest audio and Sound Physics shutdown.','entrypoints':{'client':['mcperf.compat.CompatFixes']},'mixins':['immersive-compat-fixes.mixins.json'],'depends':{'fabricloader':'>=0.19.5','minecraft':'26.2','java':'>=25','fabric-api':'*'},'suggests':{'interactivestuff':'0.7.0-26.2','fieldguide':'1.21.4+26.2-fabric'}}))
 z.writestr('immersive-compat-fixes.mixins.json',json.dumps({'required':True,'package':'mcperf.compat.mixin','plugin':'mcperf.compat.mixin.OptionalMixins','compatibilityLevel':'JAVA_25','client':[],'injectors':{'defaultRequire':1}}))
 z.writestr('LICENSE','MIT License\nCopyright (c) 2026 Immersive Minecraft contributors\n\nPermission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:\n\nThe above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.\n\nTHE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.\n')
print('Built compatibility addon')
