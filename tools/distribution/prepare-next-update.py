"""Advance a reviewed pack snapshot's version and updater sequence before build.
Does not publish anything or read/import live game files.
"""
from pathlib import Path
import argparse,json,re,time
parser=argparse.ArgumentParser();parser.add_argument('--version',required=True);args=parser.parse_args()
if not re.fullmatch(r'\d{4}\.\d{2}\.\d{2}\.\d+',args.version):raise SystemExit('Use YYYY.MM.DD.N for the reviewed release version.')
ROOT=Path(__file__).resolve().parent
for name in ['manifest.json','manifest-vanilla.json']:
    path=ROOT/'release/pack'/name;data=json.loads(path.read_text(encoding='utf-8'));data['version']=args.version;path.write_text(json.dumps(data,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
path=ROOT/'release/update-trust.json';trust=json.loads(path.read_text(encoding='utf-8'));trust['BuiltSequence']=max(int(time.time()*1000),trust['BuiltSequence']+1);path.write_text(json.dumps(trust,indent=2)+'\n',encoding='utf-8')
print('Advanced reviewed snapshot version and monotonic update sequence. Bundle/build/validate before publishing.')
