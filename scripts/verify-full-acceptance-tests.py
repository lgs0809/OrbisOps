#!/usr/bin/env python3
"""Count only fresh XML matching actual executed classes; skipped tests are separate."""
import argparse,json,re,hashlib,datetime as dt,xml.etree.ElementTree as ET
from pathlib import Path

def main(coord,output):
 if output.exists():raise ValueError('Prior evidence retained')
 d=json.loads(coord.read_text());base=coord.parent;prefix=coord.name.removesuffix('-coordinator.json');stages=[];allkeys=set()
 for stage in d['stages']:
  name=stage['stage'];log=base/(prefix+'-'+name+'.log');archive=base/(prefix+'-'+name+'-surefire')
  if 'completedAt' not in stage:raise ValueError('Stage not complete')
  start=dt.datetime.fromisoformat(stage['startedAt']).timestamp();end=dt.datetime.fromisoformat(stage['completedAt']).timestamp()
  module=None;running=[]
  for line in log.read_text(errors='replace').splitlines():
   match=re.match(r'\[INFO\] ---.* @ ([^ ]+) ---$',line)
   if match:module=match.group(1)
   match=re.match(r'\[INFO\] Running (.+)$',line)
   if match:
    if not module:raise ValueError('Test log module not observed')
    running.append((module,match.group(1)))
  totals=dict(tests=0,failures=0,errors=0,skipped=0);fresh=[];old=[];failed=[];skips=[]
  for p in archive.glob('server/*/target/surefire-reports/TEST-*.xml'):
   root=ET.parse(p).getroot();row={'module':p.parts[-4],'name':root.get('name'),'path':str(p),'mtime':p.stat().st_mtime,'xmlSha256':hashlib.sha256(p.read_bytes()).hexdigest(),**{k:int(root.get(k,'0')) for k in totals}}
   if not start-.01<=row['mtime']<=end+3:old.append(row);continue
   fresh.append(row)
   for k in totals:totals[k]+=row[k]
   for c in root.findall('testcase'):
    issues=[{'type':x.tag,'message':x.get('message',''),'detail':(x.text or '')[:3000]} for x in c if x.tag in ['failure','error']]
    if issues:failed.append({'module':row['module'],'class':row['name'],'case':c.get('name'),'issues':issues})
    if c.find('skipped') is not None:skips.append({'module':row['module'],'class':row['name'],'case':c.get('name'),'reason':c.find('skipped').get('message','')})
  keys={(r['module'],r['name']) for r in fresh};runs=set(running);duplicates=sorted(allkeys & keys);allkeys.update(keys)
  stages.append({'stage':name,'exitCode':stage['exitCode'],'totals':totals,'freshXmlCount':len(fresh),'runningCount':len(running),'runningWithoutFreshXml':sorted(runs-keys),'freshXmlWithoutRunning':sorted(keys-runs),'duplicateRunningClasses':len(running)!=len(runs),'duplicateAcrossStages':duplicates,'oldXmlExcluded':old,'failedCases':failed,'skippedCases':skips,'freshSuites':fresh,'logSha256':hashlib.sha256(log.read_bytes()).hexdigest()})
 totals={k:sum(s['totals'][k] for s in stages) for k in ['tests','failures','errors','skipped']}
 checks={'twoSuccessfulStages':len(stages)==2 and all(s['exitCode']==0 for s in stages),'sourceStable':d.get('sourceStable') is True,'actualCompiledInstalledClassesStable':d.get('classArtifactsStable') is True and all(m['identicalCompiledAndInstalledClasses'] for m in d.get('ordinaryClassArtifacts',{}).values()),'allExecutedClassesHaveFreshXML':all(not s['runningWithoutFreshXml'] and not s['freshXmlWithoutRunning'] and not s['duplicateRunningClasses'] and not s['duplicateAcrossStages'] for s in stages),'noAssertionOrExecutionFailures':totals['failures']==totals['errors']==0,'ordinaryExecuted':bool(stages and stages[0]['totals']['tests']),'nativeArchitectureExecuted':len(stages)==2 and stages[1]['totals']['tests']>0}
 report={'status':'PASS' if all(checks.values()) else 'FAIL','reviewedAt':dt.datetime.now(dt.timezone.utc).isoformat(),'checks':checks,'totals':totals,'passed':totals['tests']-totals['failures']-totals['errors']-totals['skipped'],'sourceHash':d['sourceBefore']['hash'],'jarSha256':d.get('jarSha256'),'coordinatorSha256':hashlib.sha256(coord.read_bytes()).hexdigest(),'stages':stages}
 output.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n');print(json.dumps({k:v for k,v in report.items() if k!='stages'},ensure_ascii=False))

if __name__=='__main__':
 parser=argparse.ArgumentParser();parser.add_argument('--coordinator',type=Path,required=True);parser.add_argument('--output',type=Path,required=True);args=parser.parse_args();main(args.coordinator,args.output)
