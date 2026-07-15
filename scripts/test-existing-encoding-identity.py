#!/usr/bin/env python3
"""Verify one full real local encoding and its atomic cache provenance.

Uses only a named synthetic text fixture, never imports historical vectors or
writes business state. All requests, vector metadata and native identity remain
in a fresh receipt. This is not a retrieval-quality or throughput benchmark.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import math
from pathlib import Path
import struct
import subprocess
import urllib.error
import urllib.request

BASE = 'http://127.0.0.1:8110'


def request(path, body=None):
    data = None if body is None else json.dumps(body, ensure_ascii=False).encode()
    req = urllib.request.Request(BASE + path, data=data, headers={'Content-Type': 'application/json'})
    try:
        with urllib.request.urlopen(req, timeout=190 if body else 5) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as error:
        return error.code, json.load(error)


def native():
    row = json.loads(subprocess.check_output(['docker', 'inspect', 'embedding-model'], text=True))[0]
    return {'containerId': row['Id'], 'imageId': row['Image'],
            'sourceDigest': (row['Config'].get('Labels') or {}).get('orbisops.retrieval.source'),
            'restartCount': row['RestartCount'], 'processStartedAt': row['State']['StartedAt'],
            'processPid': row['State']['Pid']}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--text-file', type=Path, required=True)
    parser.add_argument('--expect-uncertified-before', action='store_true')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists() or args.output.with_suffix('.model.log').exists():
        raise ValueError('Choose a fresh evidence stem')
    text = args.text_file.read_text(encoding='utf-8')
    started = datetime.now(timezone.utc).isoformat()
    proof = {'status': 'RUNNING', 'startedAt': started, 'scope': 'ACTUAL_ATOMIC_ENCODING_IDENTITY',
             'sourcePath': str(args.text_file.resolve()), 'textSha256': hashlib.sha256(text.encode()).hexdigest(),
             'textCharacters': len(text), 'actualModelHttpAttempts': 0, 'checks': {},
             'boundary': 'One complete synthetic input and native runtime. No business publication, historical vector certification, formal retrieval quality, or performance claim.'}
    def save():
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(proof, ensure_ascii=False, indent=2) + '\n')
    try:
        proof['before'] = native()
        code, ready = request('/ready')
        proof['readyBefore'] = ready
        if code != 200 or ready['inference']['busy']:
            raise ValueError('Shared encoder busy; no model request was made')
        code, before_identity = request('/v1/embedding-identity?input_type=document&dimensions=1024')
        proof['identityBefore'] = {'httpStatus': code, 'body': before_identity}
        if args.expect_uncertified_before:
            proof['checks']['freshProcessUnknownProfileFailsClosed'] = code == 503
        body = {'model': 'Qwen/Qwen3-VL-Embedding-2B', 'input': [text],
                'input_type': 'document', 'dimensions': 1024}
        proof['requestBody'] = body
        proof['actualModelHttpAttempts'] += 1
        save()
        code, response = request('/v1/embeddings', body)
        proof['embeddingHttpStatus'] = code
        proof['response'] = response
        if code != 200:
            raise ValueError('Actual embedding did not return successfully')
        identity = response.get('encoding_identity')
        proof['checks']['actualEmbeddingHasAtomicCertification'] = bool(identity)
        if not identity:
            raise ValueError('Actual encoder was not certifiable; caching must miss')
        vector = response['data'][0]['embedding']
        profile = identity['profile']
        canonical = json.dumps(profile, ensure_ascii=False, sort_keys=True, separators=(',', ':'), allow_nan=False)
        proof['checks'].update({
            'oneCompleteInputProducesOne1024Vector': len(response['data']) == 1 and len(vector) == 1024,
            'finiteNormalizedVector': all(math.isfinite(value) for value in vector)
                and abs(math.sqrt(sum(value * value for value in vector)) - 1) < 0.0001,
            'inputHashBoundToCompleteOriginalText': identity['inputs'][0]['input_sha256'] == proof['textSha256'],
            'returnedVectorBoundToFloat32ByteHash': identity['inputs'][0]['vector_sha256'] ==
                hashlib.sha256(struct.pack('<' + 'f' * len(vector), *vector)).hexdigest(),
            'canonicalProfileHashMatchesAllReturnedFields': identity['profile_sha256'] == hashlib.sha256(canonical.encode()).hexdigest(),
            'actualOriginalModelAndRevision': profile['model'] == 'Qwen/Qwen3-VL-Embedding-2B'
                and profile['loaded_revision'] == '9f2f7e710d6d81056aa5c0a4f04764fec6bb7bda',
            'actualCpuPolicyAndOriginalParameterPrecision': profile['device'] == 'cpu'
                and profile['parameter_dtype'] == 'torch.bfloat16'
                and profile['cpu_attention'] == 'expanded-kv-fp32'
                and profile['cpu_threads'] == 8 and profile['interop_threads'] == 1,
            'nativeTokenizerPreprocessorAndPipelineAreIdentified': all(len(profile[key]) == 64
                for key in ('tokenizer_sha256', 'preprocessor_sha256', 'pipeline_sha256')),
            'wholeInputNoTruncationAndExactRequestProfile': profile['truncation'] == 'reject-over-limit'
                and profile['input_type'] == 'document' and profile['dimensions'] == 1024})
        code, current = request('/v1/embedding-identity?input_type=document&dimensions=1024')
        proof['identityAfter'] = {'httpStatus': code, 'body': current}
        proof['checks']['readerReturnsThatCertifiedProfileWithoutReencoding'] = code == 200 and current == {
            key: value for key, value in identity.items() if key != 'inputs'}
        for kind, dimension in [('query', 1024), ('document', 2048)]:
            code, result = request(f'/v1/embedding-identity?input_type={kind}&dimensions={dimension}')
            proof.setdefault('uncertifiedProfiles', []).append({'inputType': kind, 'dimensions': dimension,
                                                               'httpStatus': code, 'body': result})
            proof['checks'][f'uncertified_{kind}_{dimension}_misses'] = code == 503
        proof['after'] = native()
        _, proof['readyAfter'] = request('/ready')
        proof['checks']['sameProcessAndContainerWithoutRestart'] = proof['before'] == proof['after']
        proof['checks']['permitReleasedAfterRealKernel'] = not proof['readyAfter']['inference']['busy']
        proof['status'] = 'PASS' if all(proof['checks'].values()) else 'FAIL'
    except Exception as error:
        proof['status'] = 'FAIL'
        proof['errorType'] = type(error).__name__
        if isinstance(error, ValueError):
            proof['failureBoundary'] = str(error)
    finally:
        logs = subprocess.run(['docker', 'logs', '--since', started, 'embedding-model'],
                              capture_output=True, text=True, timeout=10)
        args.output.with_suffix('.model.log').write_text(logs.stdout + logs.stderr)
        proof['finishedAt'] = datetime.now(timezone.utc).isoformat()
        save()
        print(json.dumps({'status': proof['status'], 'passed': sum(proof['checks'].values()),
                          'failed': [key for key, value in proof['checks'].items() if not value],
                          'output': str(args.output)}))
    raise SystemExit(int(proof['status'] != 'PASS'))


if __name__ == '__main__':
    main()
