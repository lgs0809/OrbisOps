#!/usr/bin/env python3
"""Count only fresh XML matching actual executed classes; skipped tests are separate."""
import argparse,json,re,hashlib,datetime as dt,xml.etree.ElementTree as ET
from pathlib import Path
from collections import Counter
import subprocess

def review(coord):
 coordinator_bytes=coord.read_bytes();d=json.loads(coordinator_bytes)
 if d.get('status') == 'RUNNING':raise ValueError('Coordinator is still checking final source/class/JAR identities; wait for completion before XML review')
 base=coord.parent;prefix=coord.name.removesuffix('-coordinator.json');stages=[];allkeys=set()
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
 checks={'fullOrdinaryScope':d.get('scope','FULL')=='FULL','twoSuccessfulStages':len(stages)==2 and all(s['exitCode']==0 for s in stages),'sourceStable':d.get('sourceStable') is True,'actualCompiledInstalledClassesStable':d.get('classArtifactsStable') is True and all(m['identicalCompiledAndInstalledClasses'] for m in d.get('ordinaryClassArtifacts',{}).values()),'allExecutedClassesHaveFreshXML':all(not s['runningWithoutFreshXml'] and not s['freshXmlWithoutRunning'] and not s['duplicateRunningClasses'] and not s['duplicateAcrossStages'] for s in stages),'noAssertionOrExecutionFailures':totals['failures']==totals['errors']==0,'ordinaryExecuted':bool(stages and stages[0]['totals']['tests']),'nativeArchitectureExecuted':len(stages)==2 and stages[1]['totals']['tests']>0}
 partial=d.get('scope')=='SUBSET_WITH_FULL_NATIVE_ARCHITECTURE' and all(value for key,value in checks.items() if key!='fullOrdinaryScope')
 report={'status':'PASS' if all(checks.values()) else 'PASS_SUBSET' if partial else 'FAIL','scope':d.get('scope','FULL'),'reviewedAt':dt.datetime.now(dt.timezone.utc).isoformat(),'checks':checks,'totals':totals,'passed':totals['tests']-totals['failures']-totals['errors']-totals['skipped'],'sourceHash':d['sourceBefore']['hash'],'jarSha256':d.get('jarSha256'),'coordinatorSha256':hashlib.sha256(coordinator_bytes).hexdigest(),'stages':stages}
 return report

FIXTURE='server/orbisops-app/src/test/java/cn/lgs/orbisops/infrastructure/adapter/repository/SkillEvolutionMySqlTest.java'
SUITE='cn.lgs.orbisops.infrastructure.adapter.repository.SkillEvolutionMySqlTest'
CASE='realFiveMinuteLeaseExpiryRecoversExactlyOnceAndFencesLateWorker'
ROOT=Path(__file__).resolve().parents[1]

def current_sources():
 paths=subprocess.check_output(['git','ls-files','-z','--cached','--others','--exclude-standard'],cwd=ROOT).decode().split('\0')
 return {p:hashlib.sha256((ROOT/p).read_bytes()).hexdigest() for p in sorted(set(paths)) if p and (ROOT/p).is_file() and p.startswith('server/') and Path(p).suffix in ['.java','.xml','.sql','.properties','.yml','.yaml']}

def resources():
 return {str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for module in sorted((ROOT/'server').glob('orbisops-*')) for p in sorted((module/'target/classes').rglob('*')) if p.is_file() and p.suffix!='.class'}

def production(files):return {p:v for p,v in files.items() if '/src/test/' not in p}

def consolidated(coord,baseline_coord,baseline_resources,before_fixture):
 correction=review(coord);baseline=review(baseline_coord)
 c=json.loads(coord.read_bytes());b=json.loads(baseline_coord.read_bytes());prior_resources=json.loads(baseline_resources.read_bytes())
 if not baseline['stages'] or not correction['stages']:raise ValueError('Both runs must have complete XML and logs')
 original=baseline['stages'][0];replacement=correction['stages'][0]
 old=next((suite for suite in original['freshSuites'] if suite['name']==SUITE),None)
 new=next((suite for suite in replacement['freshSuites'] if suite['name']==SUITE),None)
 if old is None or new is None:raise ValueError('Exact original and replacement fixture suite required')
 old_xml=ET.parse(old['path']).getroot();new_xml=ET.parse(new['path']).getroot()
 old_cases=Counter(case.get('name') for case in old_xml.findall('testcase'));new_cases=Counter(case.get('name') for case in new_xml.findall('testcase'))
 real=next((case for case in new_xml.findall('testcase') if case.get('name')==CASE),None)
 before=before_fixture.read_bytes();after=(ROOT/FIXTURE).read_bytes()
 needle=b'.withCommand("--max-allowed-packet=67108864");'
 permitted=b'.withCommand("--max-allowed-packet=67108864")\n            .withUrlParam("serverTimezone","UTC");'
 old_files=b['sourceBefore']['files'];new_files=c['sourceBefore']['files']
 changes=sorted(p for p in set(old_files)|set(new_files) if old_files.get(p)!=new_files.get(p))
 old_classes=b.get('finalClassArtifacts',{});new_classes=c.get('finalClassArtifacts',{})
 failures=original['failedCases']
 native=correction['stages'][1] if len(correction['stages'])==2 else {'totals':{},'exitCode':None}
 manifest=hashlib.sha256((ROOT/'server/db/migrations/manifest.tsv').read_bytes()).hexdigest()
 checks={
  'originalFullRunCompletedWithExactlyOneExpectedFixtureError':len(baseline['stages'])==1 and original['stage']=='ordinary' and original['exitCode']==1
    and baseline['totals']['failures']==0 and baseline['totals']['errors']==1 and len(failures)==1
    and failures[0]['class']==SUITE and failures[0]['case']==CASE and all(issue['type']=='error' for issue in failures[0]['issues']),
  'originalAllExecutedSuitesHaveFreshXML':baseline['checks']['allExecutedClassesHaveFreshXML'],
  'onlyExactUTCConnectionParameterChanged':changes==[FIXTURE] and hashlib.sha256(before).hexdigest()==old_files.get(FIXTURE)
    and before.count(needle)==1 and before.replace(needle,permitted)==after and hashlib.sha256(after).hexdigest()==new_files.get(FIXTURE),
  'allProductionSourcesUnchanged':production(old_files)==production(new_files),
  'allCompiledMainClassBytesUnchanged':bool(old_classes) and set(old_classes)==set(new_classes)
    and all(old_classes[key]['classInventoryHash']==new_classes[key]['classInventoryHash'] and old_classes[key]['compiledClassCount']==new_classes[key]['compiledClassCount'] for key in old_classes),
  'all27ProductionResourceBytesUnchanged':len(prior_resources['files'])==27 and prior_resources['files']==resources()
    and prior_resources['files']==c.get('compiledResourcesBefore',{}).get('files')==c.get('compiledResourcesAfter',{}).get('files'),
  'migrationManifestUnchanged':prior_resources.get('migrationManifestSha256')==c.get('migrationManifestSha256')==manifest,
  'originalAndCorrectionSourcesStable':b.get('sourceStable') is True and c.get('sourceStable') is True,
  'currentSourceIsExactlyTheCorrectionRun':current_sources()==new_files==c['sourceAfter']['files'],
  'correctionScopeExplicitAndFullyReviewed':correction['status']=='PASS_SUBSET' and c.get('scope')=='SUBSET_WITH_FULL_NATIVE_ARCHITECTURE'
    and c.get('ordinaryTests')=='SkillEvolutionMySqlTest',
  'sameComplete101FixtureCasesAllPass':old['tests']==new['tests']==101 and old_cases==new_cases and new['failures']==new['errors']==new['skipped']==0,
  'realLeaseAndCooldownExecutedWithoutSkippedCase':c.get('realLeaseWaitEnabled') is True and real is not None
    and real.find('skipped') is None and real.find('error') is None and real.find('failure') is None and float(real.get('time','0'))>=355,
  'originalNativeArchitectureGatePassed':native.get('stage')=='architecture' and native['exitCode']==0 and native['totals'].get('tests',0)>0
    and native['totals']['failures']==native['totals']['errors']==0
    and '-Dsurefire.includeJUnit5Engines=archunit' in c['stages'][1]['command'],
  'newPackagedArtifactMatchesCompiledInstalledClasses':correction['checks']['actualCompiledInstalledClassesStable'] and c.get('classArtifactsStable') is True
    and c.get('jarSha256')==hashlib.sha256((ROOT/'server/orbisops-app/target/orbisops-app.jar').read_bytes()).hexdigest(),
 }
 totals={key:original['totals'][key]-old[key]+new[key]+native['totals'].get(key,0) for key in ['tests','failures','errors','skipped']}
 return {'status':'PASS_CONSOLIDATED' if all(checks.values()) else 'FAIL','scope':'ONE_UTC_FIXTURE_CORRECTION_WITH_IDENTICAL_PRODUCTION_BYTES',
  'singleRunZeroFailureClaim':False,'reviewedAt':dt.datetime.now(dt.timezone.utc).isoformat(),'checks':checks,
  'totals':totals,'passed':totals['tests']-totals['skipped']-totals['failures']-totals['errors'],
  'originalOrdinaryStatus':baseline['status'],'originalOrdinaryTotals':original['totals'],
  'replacementFixtureTotals':{key:new[key] for key in ['tests','failures','errors','skipped']},'nativeArchitectureTotals':native['totals'],
  'baselineSourceHash':b['sourceBefore']['hash'],'sourceHash':c['sourceBefore']['hash'],
  'productionSourceHash':hashlib.sha256(json.dumps(production(new_files),sort_keys=True,separators=(',',':')).encode()).hexdigest(),
  'jarSha256':c['jarSha256'],'packagingLogSha256':replacement['logSha256'],
  'baselineCoordinator':str(baseline_coord),'baselineCoordinatorSha256':hashlib.sha256(baseline_coord.read_bytes()).hexdigest(),
  'replacementCoordinator':str(coord),'replacementCoordinatorSha256':hashlib.sha256(coord.read_bytes()).hexdigest(),
  'baselineResourceEvidence':str(baseline_resources),'originalFixtureEvidence':str(before_fixture),
  'stages':[dict(original,stage='original-ordinary'),dict(replacement,stage='fixture-replacement-package'),native]}

def main(coord,output,baseline=None,baseline_resources=None,before_fixture=None):
 if output.exists():raise ValueError('Prior evidence retained')
 if any([baseline,baseline_resources,before_fixture]) and not all([baseline,baseline_resources,before_fixture]):
  raise ValueError('A consolidation requires all original coordinator/resource/fixture evidence')
 report=consolidated(coord,baseline,baseline_resources,before_fixture) if baseline else review(coord)
 output.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
 print(json.dumps({k:v for k,v in report.items() if k!='stages'},ensure_ascii=False))

if __name__=='__main__':
 parser=argparse.ArgumentParser();parser.add_argument('--coordinator',type=Path,required=True);parser.add_argument('--output',type=Path,required=True)
 parser.add_argument('--baseline-coordinator',type=Path);parser.add_argument('--baseline-resources',type=Path);parser.add_argument('--baseline-fixture-source',type=Path)
 args=parser.parse_args();main(args.coordinator,args.output,args.baseline_coordinator,args.baseline_resources,args.baseline_fixture_source)
