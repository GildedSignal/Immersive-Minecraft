from pathlib import Path
import zipfile,hashlib,json
R=Path(__file__).resolve().parent
G=Path.home()/'AppData/Local/ImmersiveMinecraft/data/instances/immersive-minecraft/.minecraft'
original=next((G/'resourcepacks').glob('Recolourful*'))
target=R/'Recolourful-26.2-corner-fixed.zip'
entry='26.1-26.2/assets/minecraft/shaders/core/text.vsh'
with zipfile.ZipFile(original) as before,zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED) as after:
 source=before.read(entry)
 assert b'#define USE_RED_CHANNEL' not in source
 modified=source.replace(b'#version 330',b'#version 330\n\n#define USE_RED_CHANNEL',1)
 for info in before.infolist():after.writestr(info,modified if info.filename==entry else before.read(info))
with zipfile.ZipFile(original) as before,zipfile.ZipFile(target) as after:
 changed=[n for n in before.namelist() if before.read(n)!=after.read(n)]
 assert changed==[entry]
(R/'ui-patch-validation.json').write_text(json.dumps({'originalSha256':hashlib.sha256(original.read_bytes()).hexdigest(),'patchedSha256':hashlib.sha256(target.read_bytes()).hexdigest(),'changedEntries':changed,'edit':'Enable existing texture red-channel corner markers in 26.2 text vertex shader','nativeRenderPending':True},indent=2)+'\n')
print('One shader changed; every other archive entry unchanged')
