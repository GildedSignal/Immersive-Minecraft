from pathlib import Path
import base64,difflib,hashlib,json,subprocess,urllib.request,zipfile,shutil
R=Path(__file__).resolve().parent;W=R.parents[1];D=W/'tools/distribution';P=D/'release/pack'
def digest(data):return hashlib.sha256(data).hexdigest()
manifest=json.loads((P/'manifest.json').read_text(encoding='utf-8'));row=next(f for f in manifest['files'] if f['path'].startswith('resourcepacks/Recolourful '))
original=R/'recolourful-author.zip'
if not original.exists():original.write_bytes(urllib.request.urlopen(urllib.request.Request(row['url'],headers={'User-Agent':'ImmersiveMinecraft-maintenance'}),timeout=30).read())
oldhash=(row.get('patch') or {}).get('sha256',row['sha256']);oldsize=(row.get('patch') or {}).get('size',row['size'])
assert digest(original.read_bytes())==oldhash and original.stat().st_size==oldsize
target=R/'Recolourful-26.2-corner-fixed.zip';entries=[];changed=[]
with zipfile.ZipFile(original) as before,zipfile.ZipFile(target) as after:
 assert before.namelist()==after.namelist()
 for info in after.infolist():
  data=after.read(info);old=before.read(info.filename)
  entry={'name':info.filename,'source':info.filename,'sha256':digest(data),'size':len(data)}
  if old!=data:
   changed.append(info.filename);ops=[];cursor=0
   for match in difflib.SequenceMatcher(None,old,data,autojunk=False).get_matching_blocks():
    if match.b>cursor:ops.append({'insert':base64.b64encode(data[cursor:match.b]).decode()})
    if match.size:ops.append({'copy':[match.a,match.size]})
    cursor=match.b+match.size
   entry['operations']=ops
  entries.append(entry)
assert changed==['26.1-26.2/assets/minecraft/shaders/core/text.vsh']
relative='customizations/'+Path(row['path']).name+'.json';recipe=P/relative;recipe.write_text(json.dumps({'schema':1,'entries':entries},indent=2)+'\n')
output=R/'recolourful-reconstructed.zip'
if output.exists():output.unlink()
subprocess.run(['C:/Program Files/dotnet/dotnet.exe',str(D/'launcher/bin/Release/net8.0/ImmersiveMinecraft.dll'),'--patch-archive',str(original),str(recipe),str(output)],check=True,capture_output=True)
with zipfile.ZipFile(target) as expected,zipfile.ZipFile(output) as actual:assert expected.namelist()==actual.namelist() and all(expected.read(n)==actual.read(n) for n in expected.namelist())
patch={'sha256':oldhash,'size':oldsize,'recipe':relative,'recipeSha256':digest(recipe.read_bytes())}
addon=R/'immersive-compat-fixes-1.0.0+26.2.jar';addonpath='mods/'+addon.name;payload='payload/'+addonpath
shutil.copy2(addon,P/payload)
for name in ['manifest.json','manifest-vanilla.json']:
 m=json.loads((P/name).read_text(encoding='utf-8'));m['version']='2026.10.14.1'
 f=next(f for f in m['files'] if f['path']==row['path']);f.update({'sha256':digest(output.read_bytes()),'size':output.stat().st_size,'patch':patch})
 m['files']=[f for f in m['files'] if f['path']!=addonpath];m['files'].append({'path':addonpath,'sha256':digest(addon.read_bytes()),'size':addon.stat().st_size,'payload':payload})
 m.setdefault('validation',{})['compatibilityFixes']='One Recolourful 26.2 shader define; all other pack entries unchanged; native before/after render with Faithful, Iris and ImmediatelyFast. Main-thread Interactive Stuff sound callbacks; stale queued work discarded on script reload/session change. Local Field Guide journal fallback on servers without journal support; notes remain private and server discovery requires server mod.'
 (P/name).write_text(json.dumps(m,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
provenance=json.loads((P/'customization-provenance.json').read_text(encoding='utf-8'));provenance=[p for p in provenance if p['path']!=row['path']];provenance.append({'path':row['path'],'originalUrl':row['url'],'originalSha256':oldhash,'reconstructedSha256':digest(output.read_bytes()),'entryContentsIdentical':True,'changedEntries':changed,'edit':'Enable existing USE_RED_CHANNEL code for texture corner markers; no textures modified'})
(P/'customization-provenance.json').write_text(json.dumps(provenance,indent=2)+'\n')
source=P/'source/Local-customizations-source.zip';newsource=R/'source-updated.zip'
with zipfile.ZipFile(source) as before,zipfile.ZipFile(newsource,'w',zipfile.ZIP_DEFLATED) as after:
 for entry in before.infolist():
  if not entry.filename.startswith('immersive_compat_fixes/'):after.writestr(entry,before.read(entry))
 for folder in ['src','tests']:
  for f in (R/folder).rglob('*.java'):after.write(f,'immersive_compat_fixes/'+f.relative_to(R).as_posix())
 for name in ['build.py','fix_ui.py','README.md']:after.write(R/name,'immersive_compat_fixes/'+name)
shutil.copy2(newsource,source)
print('Updated both release profiles; author-download reconstruction verified')
