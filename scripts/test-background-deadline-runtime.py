#!/usr/bin/env python3
"""Verify the actual 60-second background deadline against the exact deployed JAR and a slow loopback peer.

This tests real HTTP waiting/cancellation with a synthetic provider, not model quality or business acceptance.
No configured model credentials, application rows, target state or containers are changed.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
import zipfile
from deployed_repository_probe import ROOT, JAVA_HOME


def run(output):
    if output.exists() or output.with_suffix('.log').exists():
        raise ValueError('Retain prior results; choose a fresh evidence path')
    output.parent.mkdir(parents=True, exist_ok=True)
    jar = ROOT / 'server/orbisops-app/target/orbisops-app.jar'
    digest = hashlib.sha256(jar.read_bytes()).hexdigest()
    deployed = subprocess.check_output(['docker', 'exec', 'orbisops-acceptance-backend-1',
        'sha256sum', '/opt/orbisops/orbisops.jar'], text=True).split()[0]
    if digest != deployed:
        raise RuntimeError('The exact tested application must be deployed first')
    source = ROOT / 'scripts/acceptance/BackgroundModelDeadlineProbe.java'
    with tempfile.TemporaryDirectory(prefix='ops-background-deadline-') as directory:
        root = Path(directory)
        with zipfile.ZipFile(jar) as packed:
            for entry in packed.namelist():
                if entry.startswith('BOOT-INF/lib/') and entry.endswith('.jar'):
                    (root / Path(entry).name).write_bytes(packed.read(entry))
        classpath = str(root / '*') + os.pathsep + str(root)
        compiled = subprocess.run([str(JAVA_HOME/'bin/javac'), '-cp', classpath, '-d', str(root), str(source)],
            capture_output=True, text=True, timeout=30)
        if compiled.returncode:
            raise RuntimeError('Deadline probe compile failed: ' + compiled.stderr[-2500:])
        with output.with_suffix('.log').open('x') as log:
            process = subprocess.run([str(JAVA_HOME/'bin/java'), '-Xmx256m', '-cp', classpath,
                'cn.lgs.orbisops.trigger.ops.skill.BackgroundModelDeadlineProbe', str(output)],
                stdout=log, stderr=subprocess.STDOUT, timeout=90)
        result = json.loads(output.read_text()) if output.exists() else {'status': 'FAIL', 'reason': 'PROBE_FAILED_BEFORE_REPORT'}
        result.update(jarSha256=digest, probeSourceSha256=hashlib.sha256(source.read_bytes()).hexdigest(), exitCode=process.returncode)
        output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
        print(json.dumps(result))
        if process.returncode or result['status'] != 'PASS':
            raise SystemExit(1)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    run(parser.parse_args().output)
