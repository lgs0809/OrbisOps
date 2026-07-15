#!/usr/bin/env python3
"""Add strict routes to the existing local model container, retaining its model volume.

No model downloads. The old container is retained stopped until inference is verified.
On startup failure it is restored automatically. Never prints model service credentials.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import secrets
import subprocess
import time
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
PRIVATE = ROOT / 'deploy/.acceptance-private'
NAME = 'embedding-model'
BACKUP = 'embedding-model-before-orbisops-contract'


def docker(*args):
    return subprocess.check_output(['docker', *args], text=True).strip()


def thread_count(value):
    number = int(value)
    if not 1 <= number <= 256:
        raise argparse.ArgumentTypeError('CPU thread count must be between 1 and 256')
    return number


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--cpu-threads', type=thread_count)
    parser.add_argument('--interop-threads', type=thread_count)
    parser.add_argument('--cpu-attention', choices=('native', 'expanded-kv', 'expanded-kv-fp32'))
    parser.add_argument('--output', type=Path, help='Fresh sanitized deployment receipt')
    args = parser.parse_args()
    if args.output and args.output.exists():
        parser.error('Output already exists; preserve prior evidence and use a fresh path')
    if (args.cpu_threads is not None or args.interop_threads is not None or args.cpu_attention is not None) and not args.output:
        parser.error('Runtime CPU changes require a fresh --output receipt')

    def report(value):
        value['actualOperationTime'] = datetime.now(timezone.utc).isoformat()
        if args.output:
            args.output.parent.mkdir(parents=True, exist_ok=True)
            with args.output.open('x') as stream:
                json.dump(value, stream, indent=2)
                stream.write('\n')
        print(json.dumps(value))

    original = json.loads(docker('inspect', NAME))[0]
    digest = hashlib.sha256(b''.join((ROOT / 'deploy/acceptance' / name).read_bytes() for name in ('embedding-contract.Dockerfile', 'contract_app.py', 'orbisops_retrieval_contract.py', 'patch_embedding_dimensions.py', 'qwen_embedding_length_guard.py', 'qwen_encoding_identity.py', 'qwen_inference_admission.py', 'qwen_cpu_runtime.py', 'qwen_model_residency.py', 'qwen_cpu_attention.py'))).hexdigest()
    labels = original['Config'].get('Labels') or {}
    values = dict(value.split('=', 1) for value in original['Config']['Env'])
    current_threads = int(values.get('INFERENCE_CPU_THREADS', '2'))
    current_interop = int(values.get('INFERENCE_INTEROP_THREADS', '1'))
    desired_threads = args.cpu_threads if args.cpu_threads is not None else current_threads
    desired_interop = args.interop_threads if args.interop_threads is not None else current_interop
    current_attention = values.get('INFERENCE_CPU_ATTENTION', 'native')
    desired_attention = args.cpu_attention or current_attention
    source_changed = labels.get('orbisops.retrieval.source') != digest
    runtime_changed = ((current_threads, current_interop, current_attention)
                       != (desired_threads, desired_interop, desired_attention))
    if not source_changed and not runtime_changed:
        report({'status': 'ALREADY_DEPLOYED_INFERENCE_TEST_REQUIRED',
                'sourceDigest': digest, 'imageId': original['Image'],
                'requestedCpuThreads': desired_threads, 'requestedInteropThreads': desired_interop,
                'requestedCpuAttention': desired_attention})
        return
    assert original['HostConfig']['NetworkMode'] == 'bridge'
    assert original['HostConfig']['Binds'] == ['embedding-model-cache:/models']
    assert original['HostConfig']['PortBindings'] == {'8110/tcp': [{'HostIp': '127.0.0.1', 'HostPort': '8110'}]}
    assert not original['HostConfig']['Memory'] and not original['HostConfig']['NanoCpus']
    changed = docker('diff', NAME)
    paths = [line.split(' ', 1)[1] for line in changed.splitlines()
             if ('/app/' in line or 'site-packages/' in line) and '__pycache__' not in line]
    actual_files = json.loads(docker('exec', NAME, 'python', '-c',
                                    'import os,json,sys; print(json.dumps([p for p in json.loads(sys.argv[1]) if os.path.isfile(p)]))',
                                    json.dumps(paths)))
    assert not actual_files, 'Preserve untracked container source changes first'
    assert not any(line.startswith('D ') and '__pycache__' not in line for line in changed.splitlines())
    PRIVATE.mkdir(parents=True, exist_ok=True)
    backup = PRIVATE / 'embedding-container-before-contract.json'
    if not backup.exists():
        with backup.open('x') as stream:
            os.chmod(backup, 0o600)
            json.dump(original, stream)
    credential = PRIVATE / 'retrieval-contract.json'
    if not credential.exists():
        with credential.open('x') as stream:
            os.chmod(credential, 0o600)
            json.dump({'apiKey': secrets.token_urlsafe(36)}, stream)
    key = json.loads(credential.read_text())['apiKey']
    base = json.loads(backup.read_text())
    assert docker('image', 'inspect', 'embedding-model:local', '--format', '{{.Id}}') == base['Image']
    rollback_name = BACKUP + '-' + str(time.time_ns())
    if source_changed:
        subprocess.run(['docker', 'build', '--pull=false', '--build-arg', 'BASE_IMAGE=embedding-model:local',
                    '--label', 'orbisops.retrieval.source=' + digest, '-f', 'embedding-contract.Dockerfile', '-t', 'embedding-model:orbisops-contract', '.'],
                   cwd=ROOT / 'deploy/acceptance', check=True)
    # CPU-only changes use the exact already deployed image, without a build or pull.
    image = 'embedding-model:orbisops-contract' if source_changed else original['Image']
    env_path = PRIVATE / 'retrieval-container.env'
    values.setdefault('ORBISOPS_RETRIEVAL_API_KEY', key)
    values.update(HF_HUB_OFFLINE='1', TRANSFORMERS_OFFLINE='1',
                  INFERENCE_CPU_THREADS=str(desired_threads), INFERENCE_INTEROP_THREADS=str(desired_interop),
                  INFERENCE_CPU_ATTENTION=desired_attention)
    with env_path.open('w') as stream:
        os.chmod(env_path, 0o600)
        stream.write('\n'.join(k + '=' + v for k, v in values.items()) + '\n')
    # Do not cancel a live shared kernel or release its permit. Coordinate callers and
    # recheck admission immediately before replacing the stateless service.
    current = json.loads(docker('inspect', NAME))[0]['State']
    if not current['Running'] and not current['Pid']:
        # A supervised process already exited. There is no kernel to cancel;
        # retain this fact instead of requiring an impossible HTTP response.
        current_ready = {'inference': {'busy': False}, 'processState': current['Status'],
                         'processPid': 0, 'ready': False}
    else:
        with urllib.request.urlopen('http://127.0.0.1:8110/ready', timeout=5) as response:
            current_ready = json.load(response)
    if current_ready.get('inference', {}).get('busy') is not False:
        env_path.unlink(missing_ok=True)
        raise RuntimeError('Shared inference is busy or its admission state is unknown; data and current process preserved')
    docker('stop', NAME)
    docker('rename', NAME, rollback_name)
    created = False
    try:
        docker('run', '-d', '--name', NAME, '--restart', 'unless-stopped',
               '-p', '127.0.0.1:8110:8110', '-v', 'embedding-model-cache:/models',
               '--env-file', str(env_path), image)
        created = True
        deadline = time.monotonic() + 180
        while time.monotonic() < deadline:
            try:
                with urllib.request.urlopen('http://127.0.0.1:8110/ready', timeout=3) as response:
                    ready = json.load(response)
                if ready.get('ready'):
                    actual_cpu = ready.get('cpuRuntime', {})
                    if actual_cpu.get('intraOpThreads') != desired_threads or actual_cpu.get('interOpThreads') != desired_interop:
                        raise RuntimeError('Started process CPU configuration does not match the requested configuration')
                    if ready.get('cpuAttention', {}).get('policy') != desired_attention:
                        raise RuntimeError('Started process CPU attention policy does not match the requested configuration')
                    deployed = json.loads(docker('inspect', NAME))[0]
                    report({'status': 'STARTED_INFERENCE_TEST_REQUIRED',
                                      'endpoint': 'http://127.0.0.1:8110/orbisops',
                                      'modelVolume': 'embedding-model-cache', 'downloadedWeights': False,
                                      'sourceChanged': source_changed, 'runtimeChanged': runtime_changed,
                                      'sourceDigest': digest, 'beforeImageId': original['Image'],
                                      'cpuAttentionBefore': current_attention, 'cpuAttentionAfter': desired_attention,
                                      'imageId': deployed['Image'], 'beforeContainerId': original['Id'],
                                      'containerId': deployed['Id'], 'readinessBefore': current_ready,
                                      'readinessAfter': ready, 'rollbackContainer': rollback_name,
                                      'restoreCommands': [['docker', 'stop', NAME],
                                          ['docker', 'rename', NAME, NAME + '-before-restore-' + str(int(time.time()))],
                                          ['docker', 'rename', rollback_name, NAME], ['docker', 'start', NAME]],
                                      'restoreBoundary': 'First coordinate callers and require /ready inference.busy=false. Retain both containers and the original model volume.'})
                    return
            except OSError:
                pass
            time.sleep(2)
        raise RuntimeError('Existing model container did not become ready')
    except BaseException:
        if created:
            docker('stop', NAME)
            docker('rename', NAME, NAME + '-failed-contract-' + str(int(time.time())))
        docker('rename', rollback_name, NAME)
        docker('start', NAME)
        raise
    finally:
        env_path.unlink(missing_ok=True)


if __name__ == '__main__':
    main()
