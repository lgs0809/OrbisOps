#!/usr/bin/env python3
"""Run the real Java timeout/fallback probe with private credentials kept out of arguments and logs."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
import zipfile
ROOT = Path(__file__).resolve().parents[1]
p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--output', type=Path, required=True)
args = p.parse_args()
if args.output.exists():
    raise SystemExit('Choose a new output; existing evidence is preserved.')
container = 'orbisops-acceptance-backend-1'
jar_path = '/opt/orbisops/orbisops.jar'


def deployed_sha256():
    return subprocess.check_output(['docker', 'exec', container, 'sha256sum', jar_path], text=True).split()[0]


key = json.loads((ROOT / 'deploy/.acceptance-private/retrieval-contract.json').read_text())['apiKey']
with tempfile.TemporaryDirectory(prefix='orbisops-retrieval-java-') as directory:
    target = Path(directory)
    expected_hash = deployed_sha256()
    source = target / 'deployed.jar'
    subprocess.run(['docker', 'cp', f'{container}:{jar_path}', str(source)], check=True, capture_output=True)
    if hashlib.sha256(source.read_bytes()).hexdigest() != expected_hash:
        raise RuntimeError('Deployed artifact changed during copy')
    # Use only the tested packaged dependencies, not a guessed local Maven cache.
    with zipfile.ZipFile(source) as jar:
        for name in jar.namelist():
            if name.startswith('BOOT-INF/lib/') and name.endswith('.jar'):
                (target / Path(name).name).write_bytes(jar.read(name))
    source.unlink()
    classpath = str(target / '*')
    subprocess.run(['javac', '-cp', classpath, '-d', str(target), str(ROOT / 'scripts/acceptance/ExistingRetrievalProbe.java')], check=True)
    result = subprocess.run(['java', '-cp', classpath + os.pathsep + str(target), 'ExistingRetrievalProbe'],
                            env=dict(os.environ, RETRIEVAL_PROBE_KEY=key), text=True, capture_output=True)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    unchanged = deployed_sha256() == expected_hash
    binding = {'deployedJarSha256': expected_hash, 'runtimeUnchanged': unchanged}
    args.output.write_text(result.stdout + result.stderr + '\n' + json.dumps(binding) + '\n')
    print(result.stdout)
    raise SystemExit(result.returncode if unchanged else 1)
