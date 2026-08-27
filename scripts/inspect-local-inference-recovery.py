#!/usr/bin/env python3
"""Read actual local inference recovery evidence; never restarts or mutates a container.

The optional deployment baseline contains imageId and readiness from a prior read.
This verifies process recovery and retained model identity, not retrieval quality.
"""
import argparse
import json
import subprocess
import urllib.request
from pathlib import Path


def inspect(output, since, baseline):
    if output.exists():
        raise ValueError('Retain earlier evidence; choose a fresh output')
    previous = json.loads(baseline.read_text())
    actual = json.loads(subprocess.check_output(['docker', 'inspect', 'embedding-model'], text=True))[0]
    logs = subprocess.check_output(['docker', 'logs', '--since', since, 'embedding-model'],
                                   stderr=subprocess.STDOUT, text=True)
    lifecycle = [line for line in logs.splitlines()
                 if 'INFERENCE_RESOURCE_DEADLINE_EXCEEDED' in line or 'Application startup complete' in line]
    with urllib.request.urlopen('http://127.0.0.1:8110/ready', timeout=5) as response:
        readiness = json.load(response)
    old = previous['readiness']
    checks = {
        'actual_container_restarted': actual['RestartCount'] >= 1,
        'resource_timeout_recorded_in_requested_window': any(
            'INFERENCE_RESOURCE_DEADLINE_EXCEEDED' in line for line in lifecycle),
        'same_deployed_image': actual['Image'] == previous['imageId'],
        'same_model_cache': any(m.get('Name') == 'embedding-model-cache' for m in actual['Mounts']),
        'same_models_loaded': readiness.get('ready') is True and all(
            readiness.get(key) == old.get(key) for key in ('embeddingModel', 'rerankModel')),
        'resource_deadline_retained': readiness.get('inference', {}).get('resourceDeadlineSeconds')
            == old.get('inference', {}).get('resourceDeadlineSeconds'),
        'localhost_only': actual['NetworkSettings']['Ports'].get('8110/tcp')
            == [{'HostIp': '127.0.0.1', 'HostPort': '8110'}],
    }
    result = {'status': 'PASS_RESOURCE_RECOVERY' if all(checks.values()) else 'FAIL',
              'checks': checks, 'restartCount': actual['RestartCount'], 'baseline': str(baseline),
              'startedAt': actual['State']['StartedAt'], 'imageId': actual['Image'],
              'readiness': readiness, 'lifecycleLogs': lifecycle, 'since': since,
              'modelQuality': 'NOT_ASSESSED', 'boundary': 'Read-only actual container evidence; no manufactured outcomes.'}
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({'status': result['status'], 'checks': checks}, ensure_ascii=False))
    return 0 if all(checks.values()) else 1


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--since', required=True, help='Docker log time, e.g. 2026-10-01T04:01:00Z')
    parser.add_argument('--baseline', type=Path, required=True, help='Retained deployment identity JSON')
    args = parser.parse_args()
    raise SystemExit(inspect(args.output, args.since, args.baseline))
