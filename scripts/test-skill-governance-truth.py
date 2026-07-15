#!/usr/bin/env python3
"""Verify frozen structural truth with actual classes from the exact deployed JAR.

This is a component truth check, never a model evaluation or real Task acceptance.
Development is the default. Holdout execution must be explicitly requested after
independent corpus approval and is retained separately.
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


def run(corpus, split, output):
    if output.exists() or output.with_suffix('.log').exists():
        raise ValueError('Choose a new evidence path; prior results are immutable')
    jar = ROOT/'server/orbisops-app/target/orbisops-app.jar'
    digest = hashlib.sha256(jar.read_bytes()).hexdigest()
    deployed = subprocess.check_output(['docker', 'exec', 'orbisops-acceptance-backend-1',
        'sha256sum', '/opt/orbisops/orbisops.jar'], text=True).split()[0]
    if digest != deployed: raise RuntimeError('Exact tested application must be deployed first')
    source = ROOT/'scripts/acceptance/SkillGovernanceTruthProbe.java'
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='ops-skill-truth-') as directory:
        root = Path(directory)
        with zipfile.ZipFile(jar) as packed:
            for entry in packed.namelist():
                if entry.startswith('BOOT-INF/lib/') and entry.endswith('.jar'):
                    (root/Path(entry).name).write_bytes(packed.read(entry))
        classpath = str(root/'*')+os.pathsep+str(root)
        compiled = subprocess.run([str(JAVA_HOME/'bin/javac'), '-cp', classpath, '-d', str(root), str(source)],
            capture_output=True, text=True, timeout=30)
        if compiled.returncode: raise RuntimeError(compiled.stderr[-3000:])
        with output.with_suffix('.log').open('x') as log:
            process = subprocess.run([str(JAVA_HOME/'bin/java'), '-Xmx128m', '-cp', classpath,
                'cn.lgs.orbisops.acceptance.SkillGovernanceTruthProbe', str(corpus/'facts.json'),
                str(corpus/'reference.json'), split, str(output)], stdout=log, stderr=subprocess.STDOUT, timeout=30)
    if not output.exists(): raise RuntimeError('Probe exited before writing evidence')
    report = json.loads(output.read_text())
    report.update(jarSha256=digest, probeSourceSha256=hashlib.sha256(source.read_bytes()).hexdigest(),
        factsSha256=hashlib.sha256((corpus/'facts.json').read_bytes()).hexdigest(),
        referenceSha256=hashlib.sha256((corpus/'reference.json').read_bytes()).hexdigest(), exitCode=process.returncode)
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n')
    print(json.dumps({k:v for k,v in report.items() if k!='results'}, ensure_ascii=False))
    if process.returncode or report['status'] != 'PASS': raise SystemExit(1)


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--corpus', type=Path, required=True)
    parser.add_argument('--split', choices=['development','holdout'], default='development')
    parser.add_argument('--output', type=Path, required=True)
    args=parser.parse_args(); run(args.corpus, args.split, args.output)
