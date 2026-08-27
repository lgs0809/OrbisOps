#!/usr/bin/env python3
"""Rebuild only the isolated Landing MCP read adapter; keep resources, DB and backend intact."""
import argparse
import datetime as dt
import hashlib
import json
from pathlib import Path
import runpy
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]
CONTAINER = "orbisops-acceptance-landing-mcp-1"
PEER = '''
import json,os,sqlite3,urllib.request,uuid
connection=sqlite3.connect('/state/landing-mcp.sqlite')
counts=dict(connection.execute('SELECT status,COUNT(*) FROM calls GROUP BY status'))
def rpc(method,params,session=None):
 headers={'Authorization':'Bearer '+os.environ['LANDING_MCP_TOKEN'],'Content-Type':'application/json'}
 if session:headers['Mcp-Session-Id']=session
 body={'jsonrpc':'2.0','id':uuid.uuid4().hex,'method':method,'params':params}
 with urllib.request.urlopen(urllib.request.Request('http://127.0.0.1:8381/mcp',json.dumps(body).encode(),headers),timeout=10) as r:
  return json.load(r)['result'],r.headers.get('Mcp-Session-Id')
initial,session=rpc('initialize',{'protocolVersion':'2024-11-05','clientInfo':{'name':'read-adapter-deploy-check','version':'1'},'capabilities':{}})
descriptor,_=rpc('tools/list',{},session)
print(json.dumps({'callCounts':counts,'descriptor':descriptor,'activeCalls':counts.get('RECEIVED',0)},sort_keys=True))
'''


def inspect():
    value = json.loads(subprocess.check_output(["docker", "inspect", CONTAINER]))[0]
    return {"containerId": value["Id"], "image": value["Image"], "mounts": value["Mounts"],
            "networkMode": value["HostConfig"]["NetworkMode"]}


def peer():
    return json.loads(subprocess.check_output(["docker", "exec", "-i", CONTAINER, "python3", "-"], input=PEER.encode()))


def main(output):
    if output.exists():
        raise ValueError("Retain previous evidence; use a fresh output")
    result = {"startedAt": dt.datetime.now(dt.timezone.utc).isoformat(), "status": "RUNNING",
              "boundary": "Isolated read-adapter deployment, zero target configuration writes, zero model calls",
              "sourceHashes": {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest()
                               for p in [ROOT / "deploy/acceptance" / n for n in
                                         ["landing-mcp.py", "landing-mcp.Dockerfile", "order_request_observation.py", "test_order_request_observation.py"]]}}
    log = output.with_suffix(".log")
    try:
        with log.open("w") as stream:
            tests = subprocess.run(["python3", "-m", "unittest", "-v", "test_order_request_observation"],
                                   cwd=ROOT / "deploy/acceptance", stdout=stream, stderr=subprocess.STDOUT)
            result["httpCounterexampleTestsExit"] = tests.returncode
            if tests.returncode:
                raise RuntimeError("Actual HTTP counterexamples failed; adapter not deployed")
            result["before"] = inspect()
            result["beforePeer"] = peer()
            if result["beforePeer"]["activeCalls"]:
                raise RuntimeError("Adapter has in-flight RPCs; deployment refused")
            tag = "orbisops/landing-mcp:retained-" + str(time.time_ns())
            subprocess.run(["docker", "image", "tag", result["before"]["image"], tag], check=True)
            result["retainedOldImageTag"] = tag
            compose = runpy.run_path(str(ROOT / "scripts/local-acceptance.py"))
            command = ["docker", "compose", "--project-name", compose["PROJECT"], "--env-file", str(compose["ENV"]),
                       "-f", "compose.yml", "-f", "deploy/compose.acceptance.yml", "--profile", "landing"]
            for tail in [["build", "landing-mcp"], ["up", "-d", "--no-build", "--no-deps", "landing-mcp"]]:
                subprocess.run(command + tail, cwd=ROOT, stdout=stream, stderr=subprocess.STDOUT, check=True)
        end = time.monotonic() + 60
        while True:
            try:
                result["afterPeer"] = peer()
                break
            except subprocess.CalledProcessError:
                if time.monotonic() >= end:
                    raise
                time.sleep(1)
        result["after"] = inspect()
        result["checks"] = {
            "unchangedToolDescriptor": result["beforePeer"]["descriptor"] == result["afterPeer"]["descriptor"],
            "unchangedDurableCalls": result["beforePeer"]["callCounts"] == result["afterPeer"]["callCounts"],
            "samePersistentMounts": result["before"]["mounts"] == result["after"]["mounts"],
            "sameBackendNetworkNamespace": result["before"]["networkMode"] == result["after"]["networkMode"],
            "newImage": result["before"]["image"] != result["after"]["image"],
        }
        result["status"] = "PASS" if all(result["checks"].values()) else "FAIL_DEPLOYMENT_CHECK"
    except Exception as error:
        result.update(status="FAIL_RETAINED", errorType=type(error).__name__, message=str(error))
    result.update(completedAt=dt.datetime.now(dt.timezone.utc).isoformat(), logSha256=hashlib.sha256(log.read_bytes()).hexdigest())
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps({k: result.get(k) for k in ["status", "startedAt", "completedAt", "checks", "errorType", "message"]}, ensure_ascii=False))
    raise SystemExit(0 if result["status"] == "PASS" else 1)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    main(parser.parse_args().output)
