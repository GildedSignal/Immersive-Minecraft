"""Bundle previously reviewed public inputs without regenerating recipes or source."""
from pathlib import Path
import json,zipfile,hashlib
ROOT=Path(__file__).resolve().parent;pack=ROOT/'release/pack'
full=json.loads((pack/'manifest.json').read_text(encoding='utf-8'))
vanilla=json.loads((pack/'manifest-vanilla.json').read_text(encoding='utf-8'))
assert full['id']=='immersive-minecraft' and vanilla['id']=='immersive-minecraft-vanilla'
assert full['version']==vanilla['version'] and full['releaseReady'] and vanilla['releaseReady']
out=ROOT/'release/release-pack.zip'
with zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
    for file in sorted(pack.rglob('*')):
        if not file.is_file():continue
        relative=file.relative_to(pack).as_posix()
        assert file.name not in {'servers.dat','level.dat','options.txt','accounts.json','world-mode.json'}
        assert not any(part.startswith('.friendpack') or part in {'saves','logs','cache'} for part in file.relative_to(pack).parts),relative
        archive.write(file,relative)
(ROOT/'release/package-result.json').write_text(json.dumps({'schema':1,'version':full['version'],'ready':True,'bytes':out.stat().st_size,'sha256':hashlib.sha256(out.read_bytes()).hexdigest(),'artifacts':len(full['files']),'vanillaArtifacts':len(vanilla['files'])},indent=2)+'\n',encoding='utf-8')
print('Bundled public release',full['version'],len(full['files']),'full /',len(vanilla['files']),'protected artifacts')
