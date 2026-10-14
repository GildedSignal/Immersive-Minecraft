from pathlib import Path
import subprocess,zipfile,json,shutil,sys
R=Path(__file__).resolve().parent;B=R.parent;W=B.parents[1]
J=Path.home()/'AppData/Local/Packages/Microsoft.4297127D64EC6_8wekyb3d8bbwe/LocalCache/Local/runtime/java-runtime-epsilon/windows-x64/java-runtime-epsilon/bin'
G=Path.home()/'AppData/Local/ImmersiveMinecraft/data/instances/immersive-minecraft/.minecraft'
M=Path.home()/'AppData/Roaming/ModrinthApp/meta'
fixed='--fixed' in sys.argv
all_ui='--all-ui' in sys.argv
game=R/('all-ui' if all_ui else 'after' if fixed else 'before');game.mkdir(exist_ok=True);mods=game/'mods';mods.mkdir(exist_ok=True)
cp=(B/'classpath.txt').read_text();out=R/'classes';out.mkdir(exist_ok=True)
args=['-proc:none','-encoding','UTF-8','-cp',cp,'-d',str(out),*map(str,(R/'src').rglob('*.java'))]
(R/'javac.args').write_text('\n'.join('"'+a.replace('\\','/')+'"' for a in args))
p=subprocess.run([str(J/'javac.exe'),'@'+str(R/'javac.args')],capture_output=True,text=True,encoding='utf-8');print(p.stdout+p.stderr)
if p.returncode:raise SystemExit(p.returncode)
with zipfile.ZipFile(mods/'compat-runtime-probe.jar','w',zipfile.ZIP_DEFLATED) as z:
 for f in out.rglob('*.class'):z.write(f,f.relative_to(out).as_posix())
 z.writestr('fabric.mod.json',json.dumps({'schemaVersion':1,'id':'compat_runtime_probe','version':'1.0.0','environment':'client','entrypoints':{'client':['compatprobe.Probe']},'mixins':['probe.mixins.json']}))
 z.writestr('probe.mixins.json',json.dumps({'required':True,'package':'compatprobe.mixin','compatibilityLevel':'JAVA_25','client':['HiddenWindowMixin'],'injectors':{'defaultRequire':1}}))
for pattern in ['fabric-api-*','fieldguide-*','scholar-*','ForgeConfigAPIPort-*','forgeconfigapiport-*','interactivestuff-*','iris-fabric-*','sodium-fabric-*','ImmediatelyFast-*']:
 for p in (G/'mods').glob(pattern):shutil.copy2(p,mods/p.name)
if fixed:
 for p in mods.glob('immersive-compat-fixes-*.jar'):p.unlink()
 shutil.copy2(B/'immersive-compat-fixes-1.1.0+26.2.jar',mods)
packs=game/'resourcepacks';packs.mkdir(exist_ok=True);selected=['vanilla']
for name in ['Faithful 64x - Release 14.zip','Recolourful Containers 3.1.4 (1.19.4+).zip','Faithful-Health-Hunger-26.2.zip']:
 shutil.copy2(B/'Recolourful-26.2-corner-fixed.zip' if fixed and name.startswith('Recolourful') else G/'resourcepacks'/name,packs/name);selected.append('file/'+name)
(game/'options.txt').write_text('guiScale:2\nmaxFps:30\nenableVsync:false\nfullscreen:false\nlang:en_us\nsoundCategory_master:0.0\nonboardAccessibility:false\nresourcePacks:'+json.dumps(selected)+'\n')
base=(W/'tools/immersive-wiki/reference/compile-classpath.txt').read_text().strip()
runtime=';'.join(p for p in base.split(';') if '/profiles/' not in p and '\\profiles\\' not in p and '/reference/fabric/' not in p and '\\reference\\fabric\\' not in p)
runtime+=';'+str(M/'libraries/net/fabricmc/fabric-loader/0.19.5/fabric-loader-0.19.5.jar')
report=game/'validation.txt';report.write_text('')
args=['-Xmx3G','-Djava.library.path='+str(M/'natives/26.2-0.19.5'),'-Dcompat.probe.fixed='+str(fixed).lower(),'-Dcompat.probe.report='+str(report),'-cp',runtime,'net.fabricmc.loader.impl.launch.knot.KnotClient','--username','CompatibilityTest','--accessToken','0','--uuid','00000000000000000000000000000004','--version','26.2','--gameDir',str(game),'--assetsDir',str(M/'assets'),'--assetIndex','32','--width','1000','--height','720']
if all_ui:args.insert(0,'-Dcompat.probe.cases='+str(R/'all-ui-cases.json'))
argfile=game/'java.args';argfile.write_text('\n'.join('"'+a.replace('\\','/')+'"' for a in args))
with (game/'console.log').open('w',encoding='utf-8') as log:p=subprocess.Popen([str(J/'java.exe'),'@'+str(argfile)],cwd=game,stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
(game/'process.json').write_text(json.dumps({'pid':p.pid,'gameDir':str(game)},indent=2))
print('Started hidden isolated probe:',p.pid,game.name)
