"""Pin one reusable local runtime foundation per component, without pulling an old app tag."""
from contextlib import contextmanager
from datetime import datetime, timezone
import fcntl
import hashlib
import json
from pathlib import Path
import re
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
PRIVATE = ROOT / 'deploy/.acceptance-private'
RUNTIME_FIELDS = ('Env', 'Entrypoint', 'Cmd', 'User', 'WorkingDir', 'Volumes',
                  'ExposedPorts', 'StopSignal', 'Healthcheck', 'Shell')


def inspect(kind, reference):
    return json.loads(subprocess.check_output(['docker', kind, 'inspect', reference], text=True))[0]


def runtime_fingerprint(image):
    fields = {key: image['Config'].get(key) for key in RUNTIME_FIELDS}
    return hashlib.sha256(json.dumps(fields, sort_keys=True).encode()).hexdigest()


@contextmanager
def foundation_lock():
    PRIVATE.mkdir(mode=0o700, parents=True, exist_ok=True)
    with (PRIVATE / 'runtime-foundation.lock').open('a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        yield


def ensure_runtime_foundation(component, container):
    if not re.fullmatch(r'[a-z][a-z0-9-]*', component):
        raise ValueError('Use a canonical component identity')
    with foundation_lock():
        record = PRIVATE / ('runtime-foundation-' + component + '.json')
        deployed = inspect('container', container)
        source = inspect('image', deployed['Image'])
        if record.exists():
            saved = json.loads(record.read_text())
            try:
                actual = inspect('image', saved['tag'])
            except subprocess.CalledProcessError as error:
                raise RuntimeError('Pinned local runtime foundation was removed; restore it explicitly before deployment') from error
            if actual['Id'] != saved['imageId'] or runtime_fingerprint(actual) != saved['runtimeFingerprint']:
                raise RuntimeError('Pinned runtime identity changed; refuse an unreviewed build base')
            if runtime_fingerprint(source) != saved['runtimeFingerprint']:
                raise RuntimeError('Active runtime configuration changed; explicitly review the foundation upgrade')
            return dict(saved, reused=True)

        tag = 'orbisops/runtime-foundation:' + component + '-local'
        known = subprocess.run(['docker', 'image', 'inspect', tag], capture_output=True, text=True)
        if known.returncode == 0:
            raise RuntimeError('A foundation tag exists without its identity receipt; review it before adopting')
        alias = 'orbisops/runtime-foundation:source-' + source['Id'].split(':', 1)[1]
        subprocess.run(['docker', 'tag', source['Id'], alias], check=True, capture_output=True)
        try:
            with tempfile.TemporaryDirectory(prefix='orbisops-runtime-foundation-') as folder:
                dockerfile = Path(folder) / 'Dockerfile'
                dockerfile.write_text('ARG BASE_IMAGE\nFROM ${BASE_IMAGE}\n'
                    'LABEL orbisops.runtime.foundation="' + component + '"\n'
                    'LABEL orbisops.runtime.source-image="' + source['Id'] + '"\n')
                subprocess.run(['docker', 'build', '--pull=false', '--network=none',
                                '--build-arg', 'BASE_IMAGE=' + alias, '-t', tag, folder], check=True)
        finally:
            subprocess.run(['docker', 'image', 'rm', alias], check=True, capture_output=True)
        foundation = inspect('image', tag)
        if foundation['RootFS']['Layers'] != source['RootFS']['Layers'] or runtime_fingerprint(foundation) != runtime_fingerprint(source):
            raise RuntimeError('Foundation creation changed runtime files or configuration')
        saved = {'component': component, 'tag': tag, 'imageId': foundation['Id'],
                 'sourceImageId': source['Id'], 'runtimeFingerprint': runtime_fingerprint(source),
                 'layerCount': len(foundation['RootFS']['Layers']),
                 'createdAt': datetime.now(timezone.utc).isoformat(),
                 'boundary': 'Label-only immutable local runtime pin, no new filesystem layers. Reuse this fixed base across app deployments; protect it as a current build dependency.'}
        temporary = record.with_suffix('.json.tmp')
        temporary.write_text(json.dumps(saved, indent=2) + '\n')
        temporary.chmod(0o600)
        temporary.replace(record)
        return dict(saved, reused=False)
