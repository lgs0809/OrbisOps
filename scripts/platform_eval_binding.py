"""Evidence binding for actual deployed evaluations; no scoring or case mutation."""
import hashlib
import json


def bind(test_summary, deployment, artifacts, manifest, prior_results=()):
    if not test_summary or not deployment:
        raise RuntimeError('Formal execution requires actual complete test and verified deployment evidence')
    tested = json.loads(test_summary.read_text())
    deployed = json.loads(deployment.read_text())
    jar = artifacts['deployedJarSha256']
    if tested.get('status') != 'PASS' or deployed.get('status') != 'PASS' or \
            not tested.get('checks') or not deployed.get('checks') or \
            not all(tested['checks'].values()) or not all(deployed['checks'].values()) or \
            tested.get('jarSha256') != jar or deployed.get('jarSha256') != jar or \
            not tested.get('sourceHash') or tested['sourceHash'] != deployed.get('sourceHash') or \
            tested.get('totals', {}).get('failures') != 0 or tested.get('totals', {}).get('errors') != 0:
        raise RuntimeError('Tested source, verified deployment and actual JAR do not agree')
    retained = []
    seen_cases = set()
    seen_holdout = set()
    for path in prior_results:
        previous = json.loads(path.read_text())
        hashes = previous.get('manifest', previous)
        if any(hashes.get(key) != manifest[key] for key in ['factsSha256', 'referenceSha256']):
            raise RuntimeError('Reuse disclosure must refer to the same frozen facts/reference')
        seen_cases.update(row['caseId'] for row in previous.get('results', []))
        seen_holdout.update(row['caseId'] for row in previous.get('results', []) if row.get('split') == 'holdout')
        retained.append({'path': str(path), 'sha256': hashlib.sha256(path.read_bytes()).hexdigest(),
            'denominators': previous.get('denominators'), 'status': previous.get('status')})
    return {'testedProductionSourceSha256': tested['sourceHash'], 'deployedJarSha256': jar,
        'testSummary': {'path': str(test_summary), 'sha256': hashlib.sha256(test_summary.read_bytes()).hexdigest(),
            'totals': tested['totals'], 'passed': tested['passed']},
        'deployment': {'path': str(deployment), 'sha256': hashlib.sha256(deployment.read_bytes()).hexdigest()},
        'factsSha256': manifest['factsSha256'], 'referenceSha256': manifest['referenceSha256'],
        'previouslyExecutedCases': sorted(seen_cases), 'previouslyExecutedHoldoutCases': sorted(seen_holdout),
        'reusedResults': retained, 'newlyIndependentCaseClaim': False,
        'boundary': 'REPEATED_EXECUTION_OF_DECLARED_FROZEN_CORPUS; SEEN_CASES_RETAINED; COMPONENT_OR_STRUCTURE_SCOPE_ONLY'}
