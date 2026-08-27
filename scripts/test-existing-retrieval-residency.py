#!/usr/bin/env python3
"""Real unchanged Qwen weights: embedding -> rerank -> embedding lifecycle.

Synthetic public text only. Record failure without reinterpreting busy responses as
successful inference or using lightweight lifecycle unit tests as model evidence.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import math
from pathlib import Path
import subprocess
import time
import urllib.request
import urllib.error

ROOT = Path(__file__).resolve().parents[1]
BASE = 'http://127.0.0.1:8110'


def main(output):
    if output.exists():
        raise ValueError('Preserve evidence and choose a fresh output')
    key = json.loads((ROOT / 'deploy/.acceptance-private/retrieval-contract.json').read_text())['apiKey']
    proof = {'status': 'RUNNING', 'scope': 'REAL_EXISTING_QWEN_SYNTHETIC_TEXT_RESIDENCY',
             'startedAt': datetime.now(timezone.utc).isoformat(), 'stages': [], 'checks': {}}

    def save():
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(proof, ensure_ascii=False, indent=2) + '\n')

    def request(path, body=None, auth=False):
        headers = {'Content-Type': 'application/json'}
        if auth:
            headers['Authorization'] = 'Bearer ' + key
        req = urllib.request.Request(BASE + path, data=None if body is None else json.dumps(body).encode(), headers=headers)
        start = time.monotonic()
        try:
            with urllib.request.urlopen(req, timeout=190) as response:
                return response.status, json.load(response), time.monotonic() - start
        except urllib.error.HTTPError as error:
            return error.code, json.load(error), time.monotonic() - start

    def native():
        raw = json.loads(subprocess.check_output(['docker', 'inspect', 'embedding-model'], text=True))[0]
        return {'containerId': raw['Id'], 'imageId': raw['Image'],
                'sourceDigest': (raw['Config'].get('Labels') or {}).get('orbisops.retrieval.source'),
                'restartCount': raw['RestartCount'], 'processStartedAt': raw['State']['StartedAt']}

    try:
        proof['before'] = native()
        code, state, _ = request('/ready')
        assert code == 200 and state['ready']
        assert state['residency']['capacity'] == 1
        assert set(state['residency']['verifiedKinds']) == {'embedding', 'rerank'}
        assert not state['inference']['busy']
        proof['readinessBefore'] = state
        text = '合同尾款须在书面验收通过且收到合法发票后十五日支付。'
        embed = {'model': 'Qwen/Qwen3-VL-Embedding-2B',
                 'revision': '9f2f7e710d6d81056aa5c0a4f04764fec6bb7bda',
                 'preprocessing': 'skill-route-text-v1:nfc:instruction:mrl1024:l2',
                 'dimensions': 1024, 'kind': 'query', 'text': text}
        vectors = []
        for name, path, body, auth in [
            ('embedding_before', '/orbisops/embed', embed, True),
            ('rerank', '/v1/rerank', {'model': 'Qwen/Qwen3-VL-Reranker-2B',
                'query': '尾款付款有哪些前提？', 'documents': [text, '办公区每周检查灯具。'], 'top_n': 2}, False),
            ('embedding_after', '/orbisops/embed', embed, True)]:
            code, result, seconds = request(path, body, auth)
            stage = {'name': name, 'httpStatus': code, 'seconds': round(seconds, 3)}
            proof['stages'].append(stage)
            save()
            assert code == 200, (name, code)
            if name.startswith('embedding'):
                values = result['embedding']
                norm = math.sqrt(sum(v * v for v in values))
                assert len(values) == 1024 and abs(norm - 1) < .0001
                assert result['revision'] == embed['revision']
                stage.update(dimensions=1024, norm=norm, revision=result['revision'],
                             vectorSha256=hashlib.sha256(json.dumps(values).encode()).hexdigest())
                vectors.append(values)
            else:
                rows = result['results']
                assert len(rows) == 2 and rows[0]['index'] == 0
                assert all(math.isfinite(r['relevance_score']) for r in rows)
                stage['ranking'] = rows
            status, ready, _ = request('/ready')
            assert status == 200 and not ready['inference']['busy']
            expected = 'rerank' if name == 'rerank' else 'embedding'
            assert ready['residency']['residentKind'] == expected
            assert sum(s['status'] == 'loaded' for s in ready['modelStates'].values()) == 1
            stage['readinessAfter'] = ready
            save()
        difference = max(abs(a-b) for a,b in zip(*vectors))
        assert difference <= 1e-6
        proof['after'] = native()
        assert proof['before'] == proof['after']
        proof['checks'] = {'threeRealInferences': True, 'oneResidentAtEachReadback': True,
                          'unchangedEmbeddingAcrossReload': True, 'maxVectorDifference': difference,
                          'relatedDocumentRanksFirst': True, 'noProcessRestartDuringProbe': True}
        proof['status'] = 'PASS'
    except Exception as error:
        proof['status'] = 'FAIL'
        proof['errorType'] = type(error).__name__
        raise
    finally:
        proof['finishedAt'] = datetime.now(timezone.utc).isoformat()
        save()
        print(json.dumps({'status': proof['status'], 'output': str(output),
                          'actualStages': len(proof['stages'])}))


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--output', type=Path, required=True)
    main(p.parse_args().output)
