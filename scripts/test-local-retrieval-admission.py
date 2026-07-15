#!/usr/bin/env python3
"""Real local Qwen inference: health stays responsive and concurrent inference is explicitly rejected."""
import argparse,json,time,urllib.request,urllib.error
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

BASE='http://127.0.0.1:8110'
def request(path,body=None):
    start=time.monotonic()
    req=urllib.request.Request(BASE+path,data=None if body is None else json.dumps(body).encode(),headers={'Content-Type':'application/json'})
    try:
        with urllib.request.urlopen(req,timeout=90) as r: return r.status,json.load(r),time.monotonic()-start
    except urllib.error.HTTPError as e: return e.code,json.load(e),time.monotonic()-start

def main(output):
    if output.exists():raise ValueError('Choose a new evidence file')
    result={'status':'RUNNING','attempts':[],'fixture':'SYNTHETIC_TEXT_REAL_QWEN_NO_MODEL_SUBSTITUTION'}
    try:
        for attempt in range(5):
            with ThreadPoolExecutor(max_workers=1) as pool:
                future=pool.submit(request,'/v1/embeddings',{'input':['合成验收：统计订单支付金额与退款金额。'*30], 'dimensions':1024})
                time.sleep(.3)
                health=request('/ready')
                concurrent=request('/v1/embeddings',{'input':['第二个并发请求'], 'dimensions':1024})
                first=future.result()
            result['attempts'].append({'attempt':attempt+1,'firstStatus':first[0],'firstSeconds':round(first[2],3),
                'healthStatus':health[0],'healthSeconds':round(health[2],3),'concurrentStatus':concurrent[0],
                'concurrentSeconds':round(concurrent[2],3),'concurrentReason':concurrent[1].get('detail')})
            if first[0]==200:
                assert len(first[1]['data'][0]['embedding'])==1024
                assert health[0]==200 and health[2]<2
                assert concurrent[0]==503 and concurrent[1].get('detail')=='RETRIEVAL_BUSY' and concurrent[2]<2
                result['status']='PASS';break
            time.sleep(2**attempt)
        if result['status']!='PASS':raise RuntimeError('Unable to obtain a successful inference within five attempts')
    except Exception as e:
        result['status']='FAIL';result['errorType']=type(e).__name__;raise
    finally:
        output.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n');print(json.dumps(result,ensure_ascii=False))
if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--output',required=True,type=Path);main(p.parse_args().output)
