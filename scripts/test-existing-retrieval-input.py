#!/usr/bin/env python3
"""Replay one complete synthetic embedding request on the existing local runtime.

Does not write business state or revise inputs. Retains the body and text hashes,
whole vector, original runtime identity, actual HTTP result and model-service log.
An optional old vector is a numerical comparison, not new publication evidence.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import math
from pathlib import Path
import subprocess
import time
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
BASE = 'http://127.0.0.1:8110'


def native():
    data = json.loads(subprocess.check_output(['docker', 'inspect', 'embedding-model'], text=True))[0]
    return {'containerId': data['Id'], 'imageId': data['Image'],
            'sourceDigest': (data['Config'].get('Labels') or {}).get('orbisops.retrieval.source'),
            'restartCount': data['RestartCount'], 'processStartedAt': data['State']['StartedAt'],
            'processPid': data['State']['Pid'], 'running': data['State']['Running']}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--body', type=Path, required=True)
    parser.add_argument('--previous-vector', type=Path)
    parser.add_argument('--min-previous-cosine', type=float, default=0.999,
                        help='Predeclared numerical comparison only, not a quality metric')
    parser.add_argument('--max-previous-absolute-difference', type=float, default=0.005)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if not (0 < args.min_previous_cosine <= 1 and 0 < args.max_previous_absolute_difference < 1):
        raise ValueError('Use meaningful, predeclared vector comparison bounds')
    paths = [args.output, args.output.with_suffix('.model.log')]
    if any(p.exists() for p in paths):
        raise ValueError('Preserve earlier attempts; choose a fresh output stem')
    body_bytes = args.body.read_bytes()
    body = json.loads(body_bytes)
    texts = body['input'] if isinstance(body['input'], list) else [body['input']]
    if not texts or not all(isinstance(t, str) for t in texts):
        raise ValueError('Use complete text strings from the synthetic acceptance material')
    started = datetime.now(timezone.utc).isoformat()
    proof = {'status': 'RUNNING', 'startedAt': started, 'scope': 'ACTUAL_FULL_INPUT_MODEL_REPLAY',
             'bodyPath': str(args.body.resolve()), 'bodySha256': hashlib.sha256(body_bytes).hexdigest(),
             'inputs': [{'textSha256': hashlib.sha256(t.encode()).hexdigest(), 'characters': len(t),
                         'bytes': len(t.encode())} for t in texts],
             'requestedModel': body.get('model'), 'requestedDimensions': body.get('dimensions'),
             'checks': {}, 'modelHttpAttempted': False, 'boundary': 'Direct full synthetic input replay, not normal document publication, '
                 'formal query quality, throughput or five-second acceptance. Previous vector readback is historical.'}
    args.output.parent.mkdir(parents=True, exist_ok=True)

    def save():
        args.output.write_text(json.dumps(proof, ensure_ascii=False, indent=2) + '\n')

    def ready():
        with urllib.request.urlopen(BASE + '/ready', timeout=5) as response:
            return json.load(response)

    try:
        previous = None
        if args.previous_vector:
            previous = json.loads(args.previous_vector.read_text())
            proof['previousVectorPreflight'] = {
                'path': str(args.previous_vector.resolve()),
                'previousTextSha256': previous.get('textSha256'),
                'currentTextSha256': proof['inputs'][0]['textSha256'],
                'minimumCosine': args.min_previous_cosine,
                'maximumAbsoluteDifference': args.max_previous_absolute_difference}
            if len(texts) != 1 or previous.get('textSha256') != proof['inputs'][0]['textSha256']:
                raise ValueError('PREVIOUS_VECTOR_INPUT_IDENTITY_MISMATCH')
            old = previous.get('vector')
            if (not isinstance(old, list) or len(old) != body['dimensions']
                    or not all(isinstance(x, (int, float)) and math.isfinite(x) for x in old)):
                raise ValueError('PREVIOUS_VECTOR_SHAPE_INVALID')
        proof['before'] = native()
        proof['readinessBefore'] = ready()
        assert proof['readinessBefore']['ready'] and not proof['readinessBefore']['inference']['busy']
        save()
        request = urllib.request.Request(BASE + '/v1/embeddings', data=body_bytes,
                                         headers={'Content-Type': 'application/json'})
        start = time.monotonic()
        proof['modelHttpAttempted'] = True
        save()
        try:
            with urllib.request.urlopen(request, timeout=190) as response:
                code, result = response.status, json.load(response)
        except urllib.error.HTTPError as error:
            code, result = error.code, json.load(error)
        proof['seconds'] = round(time.monotonic() - start, 3)
        proof['httpStatus'] = code
        proof['response'] = result
        assert code == 200
        vectors = [row['embedding'] for row in result['data']]
        proof['checks'] = {'oneVectorPerWholeInput': len(vectors) == len(texts),
            'requestedDimensions': all(len(v) == body['dimensions'] for v in vectors),
            'finiteL2NormalizedVectors': all(all(math.isfinite(x) for x in v)
                and abs(math.sqrt(sum(x*x for x in v)) - 1) < 0.0001 for v in vectors)}
        if args.previous_vector:
            old = previous['vector']
            assert len(old) == len(vectors[0])
            proof['previousVector'] = {'path': str(args.previous_vector.resolve()),
                'projectVersionId': previous['projectVersionId'],
                'maxAbsoluteDifference': max(abs(a-b) for a,b in zip(old, vectors[0])),
                'cosineSimilarity': sum(a*b for a,b in zip(old, vectors[0])) /
                    (math.sqrt(sum(a*a for a in old)) * math.sqrt(sum(b*b for b in vectors[0])))}
            proof['previousVector']['minimumCosineDeclaredBeforeReplay'] = args.min_previous_cosine
            proof['previousVector']['maximumAbsoluteDifferenceDeclaredBeforeReplay'] = args.max_previous_absolute_difference
            proof['checks']['predeclaredWholeVectorNumericalComparison'] = (
                proof['previousVector']['cosineSimilarity'] >= args.min_previous_cosine
                and proof['previousVector']['maxAbsoluteDifference'] <= args.max_previous_absolute_difference)
        proof['readinessAfter'] = ready()
        proof['after'] = native()
        proof['checks']['sameProcessThroughRequest'] = proof['before'] == proof['after']
        proof['checks']['admissionReleasedAfterKernelCompleted'] = not proof['readinessAfter']['inference']['busy']
        proof['status'] = 'PASS' if all(proof['checks'].values()) else 'FAIL'
    except Exception as error:
        proof['status'] = 'FAIL'
        proof['errorType'] = type(error).__name__
        if isinstance(error, ValueError) and str(error) in (
                'PREVIOUS_VECTOR_INPUT_IDENTITY_MISMATCH', 'PREVIOUS_VECTOR_SHAPE_INVALID'):
            proof['errorCode'] = str(error)
        # Preserve failures without leaking request headers or authentication.
        if 'start' in locals() and 'seconds' not in proof:
            proof['seconds'] = round(time.monotonic() - start, 3)
        try:
            proof['after'] = native()
        except Exception as inspect_error:
            proof['nativeInspectionErrorType'] = type(inspect_error).__name__
    finally:
        logs = subprocess.run(['docker', 'logs', '--since', started, 'embedding-model'],
                              capture_output=True, text=True, timeout=10)
        args.output.with_suffix('.model.log').write_text(logs.stdout + logs.stderr)
        proof['modelLogExitCode'] = logs.returncode
        proof['finishedAt'] = datetime.now(timezone.utc).isoformat()
        save()
        print(json.dumps({'status': proof['status'], 'seconds': proof.get('seconds'),
                          'httpStatus': proof.get('httpStatus'), 'output': str(args.output)}))
    raise SystemExit(int(proof['status'] != 'PASS'))


if __name__ == '__main__':
    main()
