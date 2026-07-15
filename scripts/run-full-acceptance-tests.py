#!/usr/bin/env python3
"""Full ordinary/native architecture validation; do not run in parallel with another full build."""
import subprocess,os,json,datetime as dt,hashlib,zipfile,xml.etree.ElementTree as ET
from pathlib import Path
import argparse
parser=argparse.ArgumentParser(description='Run fresh ordinary install and native ArchUnit stages, retaining all reports and artifact/source bindings.')
parser.add_argument('--output-prefix',type=Path,required=True)
parser.add_argument('--maven',default='mvn')
parser.add_argument('--java-home',default=os.environ.get('JAVA_HOME'))
parser.add_argument('--real-lease-wait',action='store_true',help='Enable the existing real five-minute MySQL lease-expiry test; never changes database time.')
args=parser.parse_args()
if not args.java_home:parser.error('Supply the existing JDK via JAVA_HOME or --java-home')
r=Path(__file__).resolve().parents[1];w=args.output_prefix.resolve().parent;prefix=args.output_prefix.name
w.mkdir(parents=True,exist_ok=True)
if any(w.glob(prefix+'-*')):raise ValueError('Retain previous evidence; use a fresh output prefix')
jdk=args.java_home;env=dict(os.environ);env.update(JAVA_HOME=jdk,PATH=jdk+'/bin:'+env['PATH'],MAVEN_OPTS='-Xmx512m -XX:ActiveProcessorCount=2')
mvn=args.maven;p=w/(prefix+'-coordinator.json')
def identity():
 paths=subprocess.check_output(['git','ls-files','-z','--cached','--others','--exclude-standard'],cwd=r).decode().split('\0');files={s:hashlib.sha256((r/s).read_bytes()).hexdigest() for s in sorted(set(paths)) if s and (r/s).is_file() and s.startswith('server/') and Path(s).suffix in ['.java','.xml','.sql','.properties','.yml','.yaml']};return {'hash':hashlib.sha256(json.dumps(files,sort_keys=True,separators=(',',':')).encode()).hexdigest(),'files':files}

def class_identity():
 modules={}
 for module in sorted((r/'server').glob('orbisops-*')):
  classes=module/'target/classes'
  if not classes.is_dir():continue
  pom=ET.parse(module/'pom.xml').getroot();ns={'m':'http://maven.apache.org/POM/4.0.0'}
  artifact=pom.find('m:artifactId',ns).text
  version=pom.find('m:version',ns)
  if version is None:version=pom.find('m:parent/m:version',ns)
  installed=Path.home()/'.m2/repository/cn/lgs'/artifact/version.text/(artifact+'-'+version.text+'.jar')
  compiled={str(p.relative_to(classes)):hashlib.sha256(p.read_bytes()).hexdigest() for p in classes.rglob('*.class')}
  packaged={}
  if installed.is_file():
   with zipfile.ZipFile(installed) as jar:
    packaged={n.removeprefix('BOOT-INF/classes/'):hashlib.sha256(jar.read(n)).hexdigest() for n in jar.namelist() if n.endswith('.class') and (n.startswith('BOOT-INF/classes/') if artifact=='orbisops-app' else not n.startswith('META-INF/'))}
  modules[artifact]={'compiledClassCount':len(compiled),'installedClassCount':len(packaged),
   'classInventoryHash':hashlib.sha256(json.dumps(compiled,sort_keys=True,separators=(',',':')).encode()).hexdigest(),
   'installedClassInventoryHash':hashlib.sha256(json.dumps(packaged,sort_keys=True,separators=(',',':')).encode()).hexdigest(),
   'identicalCompiledAndInstalledClasses':compiled==packaged,'installedJar':str(installed),
   'installedJarSha256':hashlib.sha256(installed.read_bytes()).hexdigest() if installed.is_file() else None,
   'missingInstalledClasses':sorted(compiled.keys()-packaged.keys()),'extraInstalledClasses':sorted(packaged.keys()-compiled.keys())}
 return modules

x={'status':'RUNNING','startedAt':dt.datetime.now(dt.timezone.utc).isoformat(),'cpu':2,'maxHeapMiB':512,'realLeaseWaitEnabled':args.real_lease_wait,'environment':{'JAVA_HOME':jdk,'prependPATH':jdk+'/bin','MAVEN_OPTS':env['MAVEN_OPTS']},'sourceBefore':identity(),'stages':[]}
def save():p.write_text(json.dumps(x,ensure_ascii=False,indent=2)+'\n')
save()
for stage,command in [('ordinary',[mvn,'install','-Dsurefire.excludeJUnit5Engines=archunit','-Dsurefire.failIfNoSpecifiedTests=false','-DargLine=-Xmx512m -XX:ActiveProcessorCount=2']),('architecture',[mvn,'-pl','orbisops-app','test','-Dsurefire.includeJUnit5Engines=archunit','-Dsurefire.failIfNoSpecifiedTests=false','-DargLine=-Xmx512m -XX:ActiveProcessorCount=2'])]:
 if stage=='ordinary' and args.real_lease_wait:command.append('-Dorbisops.acceptance.realLeaseWait=true')
 row={'stage':stage,'startedAt':dt.datetime.now(dt.timezone.utc).isoformat(),'command':command};x['stages'].append(row);save();log=w/(prefix+'-'+stage+'.log')
 with log.open('w') as out:result=subprocess.run(command,cwd=r/'server',env=env,stdout=out,stderr=subprocess.STDOUT)
 row.update(exitCode=result.returncode,completedAt=dt.datetime.now(dt.timezone.utc).isoformat(),logSha256=hashlib.sha256(log.read_bytes()).hexdigest());(w/(prefix+'-'+stage+'.exit')).write_text(str(result.returncode)+'\n');save()
 # Preserve each stage before the next stage overwrites same-class XML.
 import shutil
 archive=w/(prefix+'-'+stage+'-surefire');archive.mkdir()
 for f in r.glob('server/*/target/surefire-reports/*'):
  if f.is_file():target=archive/f.relative_to(r);target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(f,target)
 if result.returncode!=0:break
 if stage=='ordinary':
  x['ordinaryClassArtifacts']=class_identity();save()
  if not all(m['identicalCompiledAndInstalledClasses'] for m in x['ordinaryClassArtifacts'].values()):
   x['classArtifactBindingFailure']=True;save();break
x['sourceAfter']=identity();x['finalClassArtifacts']=class_identity();x['classArtifactsStable']=x.get('ordinaryClassArtifacts')==x['finalClassArtifacts'];jar=r/'server/orbisops-app/target/orbisops-app.jar';x['sourceStable']=x['sourceBefore']['hash']==x['sourceAfter']['hash'];x['jarSha256']=hashlib.sha256(jar.read_bytes()).hexdigest() if jar.exists() else None;x['status']='PASS_PENDING_XML_REVIEW' if len(x['stages'])==2 and all(s.get('exitCode')==0 for s in x['stages']) and x['sourceStable'] and x['classArtifactsStable'] else 'FAIL';x['completedAt']=dt.datetime.now(dt.timezone.utc).isoformat();save();print(json.dumps({k:v for k,v in x.items() if k not in ['sourceBefore','sourceAfter','ordinaryClassArtifacts','finalClassArtifacts']},ensure_ascii=False),flush=True)
