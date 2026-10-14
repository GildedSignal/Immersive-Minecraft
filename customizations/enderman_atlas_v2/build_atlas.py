from pathlib import Path
import subprocess,zipfile,hashlib,json,argparse
R=Path(__file__).resolve().parent;W=R.parents[1];E=R if (R/'EndermanAtlasPatchV2.java').exists() else W/'tools/enderman-fix'
J=Path.home()/'AppData/Local/Packages/Microsoft.4297127D64EC6_8wekyb3d8bbwe/LocalCache/Local/runtime/java-runtime-epsilon/windows-x64/java-runtime-epsilon/bin'
M=Path.home()/'AppData/Roaming/ModrinthApp/meta';G=Path.home()/'AppData/Local/ImmersiveMinecraft/data/instances/immersive-minecraft/.minecraft'
out=R/'atlas-build';out.mkdir(exist_ok=True)
cp=';'.join(str(p) for p in (M/'libraries/org/ow2/asm').rglob('*9.10.1.jar'))
def run(exe,*args):
 p=subprocess.run([str(J/exe),*map(str,args)],capture_output=True,text=True,encoding='utf-8')
 if p.returncode:raise RuntimeError(p.stdout+p.stderr)
 return p.stdout.strip()
source=out/'source/mcperf/EndermanAtlasFixV2.java';source.parent.mkdir(parents=True,exist_ok=True)
source.write_text((E/'mcperf/EndermanAtlasFixV2.java').read_text(encoding='utf-8').replace('enabled=false','enabled=true'),encoding='utf-8')
print(run('javac.exe','-proc:none','-cp',cp,'-d',out,E/'EndermanAtlasPatch.java',E/'EndermanAtlasPatchV2.java',E/'EndermanAtlasAgentV2.java',E/'AtlasV2BytecodeTest.java',E/'EndermanAtlasMath.java',E/'AtlasMathTest.java',source))
parser=argparse.ArgumentParser(description='Requires the budget-V2 Physics jar before atlas V2; duplicate atlas hooks are rejected.')
parser.add_argument('--input',type=Path,default=G/'mods/physics-mod-3.2.4-mc-26.2-fabric.jar');options=parser.parse_args()
original=options.input
with zipfile.ZipFile(original) as z:(out/'PhysicsMod-before.class').write_bytes(z.read('net/diebuddies/physics/PhysicsMod.class'))
print(run('java.exe','-Xverify:all','-cp',str(out)+';'+cp,'AtlasV2BytecodeTest',out/'PhysicsMod-before.class'))
print(run('java.exe','-cp',str(out),'AtlasMathTest'))
# The tested V2 transformer is invoked by a small original staging entrypoint.
driver=out/'AtlasStage.java';driver.write_text('import java.nio.file.*;public class AtlasStage{public static void main(String[]a)throws Exception{Files.write(Path.of(a[1]),EndermanAtlasPatchV2.patch(Files.readAllBytes(Path.of(a[0]))));}}',encoding='utf-8')
run('javac.exe','-proc:none','-cp',str(out)+';'+cp,'-d',out,driver)
run('java.exe','-cp',str(out)+';'+cp,'AtlasStage',out/'PhysicsMod-before.class',out/'PhysicsMod-after.class')
target=R/'physics-mod-3.2.4-budgetV2-atlasV2-staged.jar';entry='net/diebuddies/physics/PhysicsMod.class'
with zipfile.ZipFile(original) as before,zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED) as after:
 for info in before.infolist():after.writestr(info,(out/'PhysicsMod-after.class').read_bytes() if info.filename==entry else before.read(info))
 after.write(out/'mcperf/EndermanAtlasFixV2.class','mcperf/EndermanAtlasFixV2.class')
with zipfile.ZipFile(original) as before,zipfile.ZipFile(target) as after:
 assert all(before.read(n)==after.read(n) for n in before.namelist() if n!=entry)
(R/'atlas-stage-validation.json').write_text(json.dumps({'originalSha256':hashlib.sha256(original.read_bytes()).hexdigest(),'stagedSha256':hashlib.sha256(target.read_bytes()).hexdigest(),'changedEntries':[entry],'addedEntries':['mcperf/EndermanAtlasFixV2.class'],'nativeValidationPending':True},indent=2)+'\n',encoding='utf-8')
print('Staged atlas correction; production Physics jar unchanged')
