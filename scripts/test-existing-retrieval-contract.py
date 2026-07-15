#!/usr/bin/env python3
"""Verify real existing Qwen models through strict OrbisOps HTTP routes."""
import argparse
from concurrent.futures import ThreadPoolExecutor
import json
import math
from pathlib import Path
import threading
import time
import urllib.request
import urllib.error

ROOT = Path(__file__).resolve().parents[1]
PREPROCESS = 'skill-route-text-v1:nfc:instruction:mrl1024:l2'


def main(output):
    if output.exists():
        raise SystemExit('Preserve existing evidence; choose a new output path.')
    key = json.loads((ROOT / 'deploy/.acceptance-private/retrieval-contract.json').read_text())['apiKey']
    proof = {'status': 'RUNNING', 'scope': 'REAL_EXISTING_MODELS_SYNTHETIC_TEXT', 'checks': []}
    def request(path, body, auth=True):
        headers = {'Content-Type': 'application/json'}
        if auth:
            headers['Authorization'] = 'Bearer ' + key
        req = urllib.request.Request('http://127.0.0.1:8110/orbisops' + path,
                                     data=json.dumps(body).encode(), headers=headers)
        started = time.monotonic()
        try:
            with urllib.request.urlopen(req, timeout=120) as response:
                return response.status, json.load(response), time.monotonic() - started
        except urllib.error.HTTPError as error:
            return error.code, error.read().decode(), time.monotonic() - started
    def record(name, **facts):
        proof['checks'].append({'name': name, **facts})
        output.write_text(json.dumps(proof, ensure_ascii=False, indent=2) + '\n')
    query = 'Redis 连接失败导致订单请求超时，如何排查？'
    embed = dict(model='Qwen/Qwen3-VL-Embedding-2B', revision='9f2f7e710d6d81056aa5c0a4f04764fec6bb7bda',
                 preprocessing=PREPROCESS, dimensions=1024, kind='query', text=query)
    rank = dict(model='Qwen/Qwen3-VL-Reranker-2B', revision='4bd860ac4f15ad1897a214615cccc700f8f71818',
                preprocessing=PREPROCESS, query=query,
                documents=[{'id': 'related', 'text': '排查 Redis 连接池耗尽、连接拒绝和超时，结合订单服务日志核对依赖故障。'},
                           {'id': 'unrelated', 'text': '修改静态网站的字体颜色、导航布局和品牌标志。'}])
    output.parent.mkdir(parents=True, exist_ok=True)
    try:
        for name, body, auth, expected in [('authentication', embed, False, 401),
                                          ('wrong_revision', dict(embed, revision='0' * 40), True, 400),
                                          ('wrong_dimensions', dict(embed, dimensions=2048), True, 400)]:
            code, _, _ = request('/embed', body, auth)
            assert code == expected, (name, code)
            record(name, status='PASS', http=code)
        for name, path, body in [('embedding', '/embed', embed), ('rerank', '/rerank', rank)]:
            code, result, seconds = request(path, body)
            assert code == 200, (name, code, result)
            assert all(result[k] == body[k] for k in ('model', 'revision', 'preprocessing'))
            if name == 'embedding':
                values = result['embedding']
                norm = math.sqrt(sum(v * v for v in values))
                assert len(values) == 1024 and abs(norm - 1) < .0001
                facts = {'dimensions': len(values), 'norm': norm}
            else:
                scores = {r['id']: r['score'] for r in result['ranking']}
                assert set(scores) == {'related', 'unrelated'}
                assert all(math.isfinite(v) for v in scores.values()) and scores['related'] > scores['unrelated']
                facts = {'scores': scores}
            record(name, status='PASS', seconds=seconds, withinFiveSeconds=seconds <= 5,
                   model=result['model'], revision=result['revision'], **facts)
        code, _, _ = request('/rerank', dict(rank, documents=rank['documents'][:1] * 2))
        assert code == 400
        record('duplicate_candidates', status='PASS')
        barrier = threading.Barrier(2)
        def concurrent(_):
            barrier.wait()
            return request('/rerank', rank)[0]
        with ThreadPoolExecutor(max_workers=2) as pool:
            codes = list(pool.map(concurrent, range(2)))
        assert sorted(codes) == [200, 503], codes
        record('concurrent_admission', status='PASS', httpStatuses=codes)
        proof['status'] = 'PASS'
    except Exception as error:
        proof['status'] = 'FAIL'
        proof['failure'] = str(error)
        raise
    finally:
        output.write_text(json.dumps(proof, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps(proof, ensure_ascii=False))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    main(parser.parse_args().output)
