from pathlib import Path
import subprocess
R=Path(__file__).resolve().parent
J=Path.home()/'AppData/Local/Packages/Microsoft.4297127D64EC6_8wekyb3d8bbwe/LocalCache/Local/runtime/java-runtime-epsilon/windows-x64/java-runtime-epsilon/bin'
cp=(R/'classpath.txt').read_text(encoding='utf-8')+';'+str(R/'classes')
out=R/'test-classes';out.mkdir(exist_ok=True)
for exe,args in [('javac.exe',['-proc:none','-encoding','UTF-8','-cp',cp,'-d',str(out),str(R/'tests/CompatPolicyTest.java')]),('java.exe',['-cp',str(out)+';'+cp,'CompatPolicyTest'])]:
 argfile=R/'test.args';argfile.write_text('\n'.join('"'+a.replace('\\','/')+'"' for a in args),encoding='utf-8')
 p=subprocess.run([str(J/exe),'@'+str(argfile)],capture_output=True,text=True,encoding='utf-8');print(p.stdout+p.stderr)
 if p.returncode:raise SystemExit(p.returncode)
