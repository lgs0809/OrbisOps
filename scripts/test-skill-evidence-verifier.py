#!/usr/bin/env python3
"""Synthetic positive and tamper tests for the read-only evidence verifier; no model calls."""
import argparse
import copy
import hashlib
import json
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def main(output):
    if output.exists():
        raise ValueError('Retain earlier evidence')
    encode = lambda value: json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(',', ':'))
    digest = lambda value: hashlib.sha256(value.encode()).hexdigest()
    source = encode({'json': '{"a":[1,2]}', 'plain': 'abcxyz', 'items': [{'q': 1}, {'q': 2}],
                     'escaped/a': {'~key': False}})
    values = [('/json/@json/a/1', 2), ('/plain/@chars/1:4', 'bcx'),
              ('/items/@items/1:2/0', {'q': 2}), ('/escaped~1a/~0key', False)]
    reads = []
    for path, value in values:
        raw = encode(value)
        sha = digest(raw)
        reads.append({'path': path, 'sha256': sha, 'id': digest(path+':'+sha), 'chars': len(raw)})
    valid = {'source': source, 'audit': {'sourceHash': digest(source), 'readEvidence': reads}}
    cases = [('all_path_kinds', valid, True)]
    for field, bad in [('sha256', '0'*64), ('id', '0'*64), ('chars', 999), ('path', '/missing')]:
        changed = copy.deepcopy(valid)
        changed['audit']['readEvidence'][0][field] = bad
        cases.append(('tampered_'+field, changed, False))
    changed = copy.deepcopy(valid)
    changed['source'] = '{}'
    cases.append(('tampered_source', changed, False))
    changed = copy.deepcopy(valid)
    changed['audit']['readEvidence'] = []
    cases.append(('empty_reads', changed, False))
    java = str(Path(os.environ['JAVA_HOME'])/'bin/java') if os.environ.get('JAVA_HOME') else 'java'
    checks = []
    for name, payload, succeeds in cases:
        r = subprocess.run([java, '--class-path', str(ROOT/'server/orbisops-domain/target/classes'),
                            str(ROOT/'scripts/VerifySkillEvidenceAudit.java')],
                           input=encode(payload), text=True, capture_output=True, timeout=30)
        checks.append({'name': name, 'pass': (r.returncode == 0) == succeeds, 'exitCode': r.returncode})
        if succeeds and r.returncode == 0:
            assert len(json.loads(r.stdout)['verifiedReads']) == len(values)
    output.parent.mkdir(parents=True, exist_ok=True)
    result = {'status': 'PASS' if all(c['pass'] for c in checks) else 'FAIL', 'checks': checks,
              'boundary': 'Synthetic integrity tests only, not a real model result.'}
    output.write_text(json.dumps(result, indent=2)+'\n')
    print(json.dumps(result))
    assert result['status'] == 'PASS'


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--output', type=Path, required=True)
    main(p.parse_args().output)
