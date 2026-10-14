"""Portable Java 25 source rebuild for five existing local compatibility patches.

Inputs are the user's own Minecraft jar, installed pack mods and launcher libraries.
No Minecraft binaries or account data are distributed in this source archive.
"""
from pathlib import Path
import argparse,subprocess,zipfile,urllib.request,hashlib,json,os
p=argparse.ArgumentParser()
p.add_argument('--jdk',type=Path,required=True,help='Java 25 JDK directory (contains bin/javac)')
p.add_argument('--minecraft-jar',type=Path,required=True,help='Minecraft 26.2 client jar')
p.add_argument('--mods',type=Path,required=True,help='Installed friend instance mods directory')
p.add_argument('--libraries',type=Path,required=True,help='Prism data/libraries directory')
p.add_argument('--classpath-file',type=Path,help='Optional additional compiler classpath')
p.add_argument('--output',type=Path,default=Path('build'))
a=p.parse_args();root=Path(__file__).resolve().parent;out=a.output.resolve();out.mkdir(parents=True,exist_ok=True)
ext='.exe' if os.name=='nt' else '';javac=a.jdk/'bin'/('javac'+ext);java=a.jdk/'bin'/('java'+ext)
paths=[a.minecraft_jar,*a.mods.glob('*.jar'),*sorted(a.libraries.rglob('*.jar'),key=lambda x:x.as_posix(),reverse=True)]
nested=out/'nested';nested.mkdir(exist_ok=True)
for jar in list(paths):
    with zipfile.ZipFile(jar) as z:
        for info in z.infolist():
            if info.filename.startswith('META-INF/jars/') and info.filename.endswith('.jar'):
                data=z.read(info);dest=nested/(hashlib.sha256(data).hexdigest()+'.jar');dest.write_bytes(data);paths.append(dest)
deps={
 'annotations-26.0.2.jar':'https://repo.maven.apache.org/maven2/org/jetbrains/annotations/26.0.2/annotations-26.0.2.jar',
 'jsr305-3.0.2.jar':'https://repo.maven.apache.org/maven2/com/google/code/findbugs/jsr305/3.0.2/jsr305-3.0.2.jar',
 'DistantHorizonsApi-7.0.0.jar':'https://api.modrinth.com/maven/maven/modrinth/DistantHorizonsApi/7.0.0/DistantHorizonsApi-7.0.0.jar'}
for name,url in deps.items():
    dest=out/name
    if not dest.exists():dest.write_bytes(urllib.request.urlopen(url,timeout=60).read())
    paths.append(dest)
cp=os.pathsep.join(map(str,paths))
if a.classpath_file:cp+=os.pathsep+a.classpath_file.read_text(encoding='utf-8').strip()
def compile(sources,classes,classpath):
    classes.mkdir(parents=True,exist_ok=True)
    args=['-proc:none','-encoding','UTF-8','-cp',classpath,'-d',str(classes),*map(str,sources)]
    argfile=out/'javac.args';argfile.write_text('\n'.join('"'+s.replace('\\','/')+'"' for s in args),encoding='utf-8')
    subprocess.run([str(javac),'@'+str(argfile)],check=True)
helpers=out/'helpers';compile([root/'WidenCompileApi.java',root/'SplitterComparison.java'],helpers,cp)
api=out/'minecraft-compile-api.jar'
def run_java(arguments):
    argfile=out/'java.args';argfile.write_text('\n'.join('"'+s.replace('\\','/')+'"' for s in arguments),encoding='utf-8')
    subprocess.run([str(java),'@'+str(argfile)],check=True)
run_java(['-cp',cp+os.pathsep+str(helpers),'WidenCompileApi',str(a.minecraft_jar),str(api)])
cp=str(api)+os.pathsep+cp
for project in ['emf_jit','iris_translucent_order','entity_texture_features','immersive-storms','litematica']:
    source=root/project/'src';files=list(source.rglob('*.java'))
    if project=='entity_texture_features':files=[f for f in files if f.name in {'ETFFrameNbtCache.java','MixinGameRenderer.java','MixinBlockEntity.java','MixinEntity.java'}]
    classes=out/project/'classes';compile(files,classes,cp)
    original=None
    for jar in a.mods.glob('*.jar'):
        with zipfile.ZipFile(jar) as z:
            if 'fabric.mod.json' in z.namelist() and json.loads(z.read('fabric.mod.json'),strict=False).get('id')==project:original=jar;break
    if original is None:raise RuntimeError('Missing installed dependency: '+project)
    rebuilt=out/(project+'-rebuilt.jar'); replacements={f.relative_to(classes).as_posix():f.read_bytes() for f in classes.rglob('*.class')}
    with zipfile.ZipFile(original) as old,zipfile.ZipFile(rebuilt,'w',zipfile.ZIP_DEFLATED) as new:
        for item in old.infolist():new.writestr(item,replacements.pop(item.filename,old.read(item)))
        for name,data in replacements.items():new.writestr(name,data)
    print('Rebuilt',project,rebuilt)
    if project=='emf_jit':run_java(['-cp',cp+os.pathsep+str(helpers),'SplitterComparison',str(original),str(classes)])
