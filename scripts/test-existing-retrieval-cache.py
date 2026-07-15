#!/usr/bin/env python3
"""Real document inference + exact-content cache hit; synthetic text, no writes to business data."""
import argparse
import json
from pathlib import Path
import time
import urllib.request
ROOT = Path(__file__).resolve().parents[1]
p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--output', type=Path, required=True)
a = p.parse_args()
if a.output.exists():
    raise SystemExit('Preserve existing evidence; choose another output.')
key = json.loads((ROOT / 'deploy/.acceptance-private/retrieval-contract.json').read_text())['apiKey']
body = dict(model='Qwen/Qwen3-VL-Embedding-2B', revision='9f2f7e710d6d81056aa5c0a4f04764fec6bb7bda',
            preprocessing='skill-route-text-v1:nfc:instruction:mrl1024:l2', dimensions=1024, kind='document',
            text='Cache acceptance: inspect Redis connection pool saturation and correlated request errors. ' * 12)
results = []
for _ in range(2):
    request = urllib.request.Request('http://127.0.0.1:8110/orbisops/embed', data=json.dumps(body).encode(),
                                    headers={'Content-Type':'application/json','Authorization':'Bearer '+key})
    start = time.monotonic()
    with urllib.request.urlopen(request, timeout=120) as response:
        result = json.load(response)
    results.append((time.monotonic()-start, result))
assert results[0][1] == results[1][1]
assert len(results[0][1]['embedding']) == 1024
assert results[1][0] < 1
proof = dict(status='PASS', scope='REAL_MODEL_SYNTHETIC_DOCUMENT_CACHE', firstSeconds=results[0][0],
             cachedSeconds=results[1][0], identicalVector=True, dimension=1024, revision=body['revision'])
a.output.parent.mkdir(parents=True, exist_ok=True)
a.output.write_text(json.dumps(proof, indent=2)+'\n')
print(json.dumps(proof))
