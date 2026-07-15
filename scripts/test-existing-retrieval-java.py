#!/usr/bin/env python3
"""Run the real Java timeout/fallback probe with private credentials kept out of arguments and logs."""
import argparse
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
key = json.loads((ROOT / 'deploy/.acceptance-private/retrieval-contract.json').read_text())['apiKey']
with tempfile.TemporaryDirectory(prefix='orbisops-retrieval-java-') as directory:
    target = Path(directory)
    # Use only the tested packaged dependencies, not a guessed local Maven cache.
    with zipfile.ZipFile(ROOT / 'server/orbisops-app/target/orbisops-app.jar') as jar:
        for name in jar.namelist():
            if name.startswith('BOOT-INF/lib/') and name.endswith('.jar'):
                (target / Path(name).name).write_bytes(jar.read(name))
    classpath = str(target / '*')
    subprocess.run(['javac', '-cp', classpath, '-d', str(target), str(ROOT / 'scripts/acceptance/ExistingRetrievalProbe.java')], check=True)
    result = subprocess.run(['java', '-cp', classpath + os.pathsep + str(target), 'ExistingRetrievalProbe'],
                            env=dict(os.environ, RETRIEVAL_PROBE_KEY=key), text=True, capture_output=True)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(result.stdout + result.stderr)
    print(result.stdout)
    raise SystemExit(result.returncode)
