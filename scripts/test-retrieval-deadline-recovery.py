#!/usr/bin/env python3
"""Real local Qwen timeout/recovery probe. No credentials, vectors or false quality claims in evidence."""
import argparse, http.client, json, math, time, urllib.request, urllib.error
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--output', required=True, type=Path)
a = p.parse_args()
if a.output.exists(): raise SystemExit('Choose a new evidence file.')
key = json.loads((ROOT/'deploy/.acceptance-private/retrieval-contract.json').read_text())['apiKey']
base = 'http://127.0.0.1:8110/orbisops/'
preprocess = 'skill-route-text-v1:nfc:instruction:mrl1024:l2'
records = []

def request(route, body):
    start = time.monotonic()
    try:
        req = urllib.request.Request(base+route, data=json.dumps(body).encode(),
            headers={'Authorization':'Bearer '+key, 'Content-Type':'application/json'})
        with urllib.request.urlopen(req, timeout=5) as response: result=json.load(response)
        record={'route':route,'status':'COMPLETED','seconds':time.monotonic()-start,
                'model':result.get('model'),'revision':result.get('revision')}
        if route=='embed':
            vector=result.get('embedding',[])
            assert len(vector)==1024 and all(math.isfinite(v) for v in vector)
            assert abs(sum(v*v for v in vector)-1)<0.00001
            assert result['model']==body['model'] and result['revision']==body['revision'] and result['preprocessing']==preprocess
            record['dimensions']=len(vector)
        else:
            ranking=result.get('ranking',[])
            assert len(ranking)==len(body['documents']) and {r['id'] for r in ranking}=={d['id'] for d in body['documents']}
            assert all(math.isfinite(r['score']) for r in ranking)
            assert result['model']==body['model'] and result['revision']==body['revision'] and result['preprocessing']==preprocess
            record['scores']=len(ranking)
    except urllib.error.HTTPError as error:
        payload=json.loads(error.read())
        record={'route':route,'status':'HTTP_ERROR','httpStatus':error.code,'reason':payload.get('detail'),'seconds':time.monotonic()-start}
    except (OSError, http.client.HTTPException) as error:
        record={'route':route,'status':'TRANSPORT_UNAVAILABLE','type':type(error).__name__,'seconds':time.monotonic()-start}
    records.append(record)
    return record

rerank=request('rerank',dict(model='Qwen/Qwen3-VL-Reranker-2B',revision='4bd860ac4f15ad1897a214615cccc700f8f71818',preprocessing=preprocess,
    query='Redis 连接池等待应该检查什么？',documents=[{'id':str(i),'text':'核对连接池等待数量、超时与服务资源状态，保留真实证据。'} for i in range(20)]))
deadline=time.monotonic()+30
recovered=False
while time.monotonic()<deadline:
    time.sleep(1)
    row=request('embed',dict(model='Qwen/Qwen3-VL-Embedding-2B',revision='9f2f7e710d6d81056aa5c0a4f04764fec6bb7bda',
        preprocessing=preprocess,dimensions=1024,kind='query',text='核对服务状态'))
    if row['status']=='COMPLETED': recovered=True;break
result={'status':'PASS_RECOVERY' if recovered else 'FAIL_RECOVERY','scope':'REAL_LOCAL_QWEN_TIMEOUT_RECOVERY_NOT_RANKING_QUALITY',
        'partialRankAccepted':False,'records':records}
a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
print(json.dumps(result,ensure_ascii=False));raise SystemExit(0 if recovered else 1)
