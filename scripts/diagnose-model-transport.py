#!/usr/bin/env python3
"""Compare host/container TCP and TLS to the configured model gateway, without API calls or credentials."""
import argparse
import json
import os
from pathlib import Path
import socket
import ssl
import subprocess
import time
import urllib.parse

PROBE='''import json,os,socket,ssl,time,urllib.parse
url=os.environ.get('ORBISOPS_MODEL_BASE_URL') or os.environ.get('SPRING_AI_OPENAI_BASE_URL') or ''
u=urllib.parse.urlsplit(url)
if not u.hostname:raise RuntimeError('MODEL_BASE_URL_NOT_AVAILABLE')
port=u.port or (443 if u.scheme=='https' else 80)
start=time.monotonic()
try:addresses=socket.getaddrinfo(u.hostname,port,type=socket.SOCK_STREAM)
except OSError as e:
 print(json.dumps({'dns':'FAILED','errorType':type(e).__name__}));raise SystemExit(0)
result={'dns':'PASS','dnsMs':round((time.monotonic()-start)*1000),'probes':[]}
seen=set()
for family,kind,proto,_,address in addresses:
 if family in seen:continue
 seen.add(family);start=time.monotonic();entry={'family':'IPv6' if family==socket.AF_INET6 else 'IPv4'}
 try:
  with socket.socket(family,kind,proto) as s:
   s.settimeout(5);s.connect(address);entry['tcp']='PASS';entry['tcpMs']=round((time.monotonic()-start)*1000)
   if u.scheme=='https':
    with ssl.create_default_context().wrap_socket(s,server_hostname=u.hostname) as tls:entry['tls']='PASS';entry['protocol']=tls.version()
 except OSError as e:entry.update(state='FAILED',errorType=type(e).__name__)
 entry['durationMs']=round((time.monotonic()-start)*1000);result['probes'].append(entry)
print(json.dumps(result))
'''

def main(output):
    root=Path(__file__).resolve().parents[1]
    settings=dict(line.split('=',1) for line in (root/'deploy/.env.acceptance').read_text().splitlines() if line and not line.startswith('#') and '=' in line)
    environment={**os.environ,'ORBISOPS_MODEL_BASE_URL':settings['ORBISOPS_MODEL_BASE_URL']}
    results={'scope':'TCP_TLS_ONLY_NO_MODEL_REQUESTS','credentialsSent':False}
    for name,args,env in [('host',['python3','-'],environment),('backend',['docker','exec','-i','orbisops-acceptance-backend-1','python3','-'],None)]:
        process=subprocess.run(args,input=PROBE,text=True,capture_output=True,env=env,timeout=25)
        results[name]=json.loads(process.stdout) if process.returncode==0 else {'state':'PROBE_FAILED','exitCode':process.returncode}
    output.parent.mkdir(parents=True,exist_ok=True);output.write_text(json.dumps(results,indent=2)+'\n');print(json.dumps(results))
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--output',type=Path,required=True);main(p.parse_args().output)
