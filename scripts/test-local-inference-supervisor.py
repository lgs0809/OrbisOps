#!/usr/bin/env python3
"""Verify Docker recovery of a stalled inference process in a disposable container.

The injected sleeping kernel proves process/resource recovery, not model quality.
It never mounts existing data, starts a second model, downloads weights or exposes a port.
"""
import argparse
import json
from pathlib import Path
import subprocess
import time
import uuid


def main(output):
    if output.exists():
        raise ValueError('Preserve previous evidence')
    name = 'qwen-inference-supervisor-it-' + uuid.uuid4().hex[:12]
    code = """
from pathlib import Path
import json,time
from qwen_inference_admission import GATE,infer
marker=Path('/tmp/isolated-supervisor-fixture')
if not marker.exists():
    marker.write_text('FAULT_INJECTED')
    print('FIRST_BOOT_STALLED_KERNEL',flush=True)
    infer(time.sleep,30)
    raise AssertionError('A stalled kernel must not finish normally')
result=infer(lambda:42)
print(json.dumps({'phase':'RESTORED','result':result,'inference':GATE.snapshot()}),flush=True)
"""
    report = {'scope': 'DISPOSABLE_DOCKER_PROCESS_RECOVERY_NOT_MODEL_QUALITY', 'container': name,
              'status': 'FAIL', 'mountedExistingData': False, 'downloadedWeights': False}
    try:
        subprocess.run(['docker','run','-d','--name',name,'--network','none','--restart','on-failure:1',
                        '--memory','128m','--cpus','0.5','-e','INFERENCE_RESOURCE_DEADLINE_SECONDS=0.5',
                        'embedding-model:orbisops-contract','python','-c',code],
                       check=True,capture_output=True,text=True)
        deadline = time.monotonic()+40
        while True:
            state = json.loads(subprocess.check_output(['docker','inspect',name],text=True))[0]
            if state['RestartCount'] == 1 and state['State']['Status'] == 'exited':
                break
            if time.monotonic() >= deadline:
                raise TimeoutError('Disposable supervisor did not recover within its deadline')
            time.sleep(0.25)
        logs = subprocess.run(['docker','logs',name],capture_output=True,text=True,check=True)
        restored = [json.loads(line) for line in logs.stdout.splitlines() if line.startswith('{')]
        checks = {'stalled_process_restarted_once': state['RestartCount']==1,
                  'recovered_process_finished_normally': state['State']['ExitCode']==0,
                  'fault_resource_deadline_recorded': 'INFERENCE_RESOURCE_DEADLINE_EXCEEDED' in logs.stderr,
                  'subsequent_operation_completed_and_released': len(restored)==1
                      and restored[0]['phase']=='RESTORED' and restored[0]['result']==42
                      and restored[0]['inference']['busy'] is False,
                  'no_model_volume_or_port_or_network': not state['Mounts']
                      and state['HostConfig']['NetworkMode']=='none' and not state['HostConfig']['PortBindings']}
        report.update(checks=checks,restartCount=state['RestartCount'],exitCode=state['State']['ExitCode'],
                      logs=logs.stdout+logs.stderr,status='PASS' if all(checks.values()) else 'FAIL')
        if report['status'] != 'PASS':
            raise AssertionError('Supervisor recovery invariant failed')
        # Only this uniquely named, successfully inspected disposable fixture is removed.
        subprocess.run(['docker','rm',name],check=True,capture_output=True,text=True)
        report['disposableFixtureRemoved']=True
    except Exception as error:
        report['failureClass']=type(error).__name__
        raise
    finally:
        output.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
        print(json.dumps(report,ensure_ascii=False))


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output',type=Path,required=True)
    main(parser.parse_args().output)
