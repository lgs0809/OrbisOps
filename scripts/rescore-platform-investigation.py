#!/usr/bin/env python3
"""Re-score existing development runs only; never requests models or changes original evidence."""
import argparse
import copy
import datetime as dt
import hashlib
import importlib.util
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def rescore(existing, review_path, output):
    if output.exists(): raise RuntimeError('Original evidence must remain; choose a new output')
    source = ROOT/'scripts/platform-investigation-scoring.py'
    digest = hashlib.sha256(source.read_bytes()).hexdigest()
    review = json.loads(review_path.read_text())
    if review.get('status') != 'APPROVED_STATIC_SCORER' or review.get('sourceSha256') != digest:
        raise RuntimeError('Exact independent approval required')
    spec = importlib.util.spec_from_file_location('scoring', source)
    scoring = importlib.util.module_from_spec(spec); spec.loader.exec_module(scoring)
    original = json.loads(existing.read_text())
    if not original['results'] or any(r['split'] != 'development' for r in original['results']):
        raise RuntimeError('Only already observed development runs may be re-scored here')
    if len({r['runId'] for r in original['results']}) != len(original['results']):
        raise RuntimeError('Ambiguous original run IDs')
    reference = ROOT/'deploy/.acceptance-private/platform-investigation-v1/reference.json'
    if hashlib.sha256(reference.read_bytes()).hexdigest() != original['referenceSha256']:
        raise RuntimeError('Original reference hash changed')
    truths = {r['caseId']: r for r in json.loads(reference.read_text())}
    result = copy.deepcopy(original)
    result.update(status='SAME_RUN_DEVELOPMENT_RESCORING_ONLY', rescoredAt=dt.datetime.now(dt.timezone.utc).isoformat(),
        originalReportPath=str(existing), originalReportSha256=hashlib.sha256(existing.read_bytes()).hexdigest(),
        originalDenominators=copy.deepcopy(original['denominators']),
        originalStatus=original['status'], originalScore={'pass': sum(r['status']=='PASS' for r in original['results']),
            'fail': sum(r['status']=='FAIL' for r in original['results'])},
        scoringRule={'version': scoring.VERSION, 'sourceSha256': digest, 'independentReviewPath': str(review_path),
            'independentReviewSha256': hashlib.sha256(review_path.read_bytes()).hexdigest(), 'boundary': review.get('boundary')},
        newModelExecutions=0, newPhysicalToolCalls=0,
        note='Same original run IDs, observations, model responses and safety/isolation checks. Original 7PASS/9FAIL retained. Interrupted 34-case screening remains incomplete; no holdout evaluation.')
    for row in result['results']:
        row['originalStatus'] = row['status']; row['originalChecks'] = copy.deepcopy(row['checks'])
        row['findingScore'] = scoring.score(row['answer'], truths[row['caseId']]['expectedFinding'])
        row['checks']['expectedConclusion'] = row['findingScore']['passed']
        row['status'] = 'PASS' if all(row['checks'].values()) else 'FAIL'
    result['rescoredDenominators'] = {'plannedUniqueCases': original['denominators']['plannedUniqueCases'],
        'scoredUniqueCases': len({r['caseId'] for r in result['results']}), 'scoredAttempts': len(result['results']),
        'pass': sum(r['status']=='PASS' for r in result['results']), 'fail': sum(r['status']=='FAIL' for r in result['results'])}
    # The v1 reliability summaries retain their old meaning; do not silently
    # carry them under the new scores or pretend additional attempts occurred.
    result['originalCaseReliability'] = result.pop('caseReliability', None)
    result['originalPassedAttempts'] = result.pop('passedAttempts', None)
    result['originalRuntimeP95Ms'] = result.pop('runtimeP95Ms', None)
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2)+'\n')
    print(json.dumps({'status':result['status'],'original':result['originalScore'],
        'rescored':result['rescoredDenominators'],'newModelExecutions':0},ensure_ascii=False))


if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    for key in ['existing','review','output']: parser.add_argument('--'+key,type=Path,required=True)
    args=parser.parse_args(); rescore(args.existing,args.review,args.output)
