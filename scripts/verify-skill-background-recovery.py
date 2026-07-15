#!/usr/bin/env python3
"""Verify retained local queue recovery and an overlapping foreground run; never manufacture outcomes."""
import argparse
import hashlib
import json
from pathlib import Path


def verify(paths, output):
    if output.exists():
        raise ValueError('Choose a new report; retain earlier failures')
    records = {name: json.loads(path.read_bytes()) for name, path in paths.items()}
    before, after = records['before']['facts'], records['after']['facts']
    old, current = before['job'][0], after['job'][0]
    authored_facts = records['authored']['facts']
    author = authored_facts[0] if len(authored_facts) == 1 else {}
    conclusion = author.get('authored', {})
    overlap = records['overlap'].get('facts', [])
    foreground = records['foreground']
    acceptance = records['acceptance']
    episode = next((r for r in acceptance['rows'] if r['kind'] == 'episode'), {})
    accepted = next((r for r in acceptance['rows'] if r['kind'] == 'acceptance' and r['id'] == episode.get('verifiedRef')), {})
    checks = {
        'original_failure_is_retained': old['status'] == 'FAILED' and old['attempts'] > 0,
        'same_job_run_project_and_source_recovered': all(old[k] == current[k] for k in ('jobId', 'runId', 'projectId', 'sourceId')),
        'original_full_source_is_unchanged': before['source'] == after['source'] and len(after['source']) == 1,
        'source_hash_is_valid': all(s['inputHash'] == s['actualInputHash'] for s in after['source']),
        'old_failure_audit_is_preserved': before['audit'] == after['audit'][:len(before['audit'])],
        'new_normal_retry_and_completion_are_recorded': {'job-create', 'job-run'} <= {a['action'] for a in after['audit'][len(before['audit']):]},
        'recovered_job_has_terminal_no_change_and_no_live_lease': current['status'] == 'SKIPPED' and current['leaseUntilMs'] == 0 and current['ordinaryFailureCount'] == 0,
        'actual_authored_input_and_output_hashes_match': bool(author) and author['planHash'] == author['actualPlanHash'] and author['authoredHash'] == author['actualAuthoredHash'],
        'real_approved_terra_authored_original_input': conclusion.get('authoringSource') == 'LLM' and conclusion.get('authoringModel') == 'gpt-5.6-terra' and conclusion.get('modelInputEncoding') == 'ORIGINAL' and conclusion.get('modelInputHash') == author.get('planHash'),
        'no_change_has_explanation_and_does_not_claim_publication': conclusion.get('patchType') == 'NO_CHANGE' and bool(conclusion.get('reason')) and not conclusion.get('changes') and not conclusion.get('artifacts') and not after['source'][0]['candidateId'],
        'recorded_background_and_foreground_really_overlap': len(overlap) == 1 and overlap[0]['backgroundStatus'] == 'RUNNING' and overlap[0]['foregroundStatus'] == 'RUNNING' and overlap[0]['reason'] == 'SKILL_MODEL_TRANSPORT_DEFERRED',
        'overlapping_foreground_finished_with_real_read_receipt': foreground['status'] == 'PASS' and len(foreground['run']) == 1 and foreground['run'][0]['status'] == 'SUCCEEDED' and foreground['run'][0]['runId'] == overlap[0]['foregroundRunId'] and len(foreground['calls']) == 1 and foreground['calls'][0]['status'] == 'SUCCEEDED' and foreground['calls'][0]['readOnly'] == 1,
        'normal_foreground_acceptance_is_current_and_matches_that_run': acceptance['status'] == 'PASS_INTEGRITY' and episode.get('outcome') == 'SUCCEEDED' and accepted.get('outcome') == 'SUCCEEDED' and accepted.get('sourceRun') == foreground['run'][0]['runId'] and accepted.get('checks') == 3,
        'accepted_receipt_matches_the_cross_checked_foreground_receipt': any(r['kind'] == 'receipt' and r['resultId'] == foreground['receipts'][0]['resultId'] and r['status'] == 'SUCCEEDED' and r['hashMatches'] for r in acceptance['rows']),
    }
    report = {'status': 'PASS_RECOVERY_AND_NO_CHANGE' if all(checks.values()) else 'FAIL', 'checks': checks,
              'jobId': current['jobId'], 'sourceId': current['sourceId'],
              'foregroundRunId': foreground['run'][0]['runId'], 'foregroundEpisodeId': episode.get('episodeId'),
              'inputs': {name: {'path': str(path), 'sha256': hashlib.sha256(path.read_bytes()).hexdigest()} for name, path in paths.items()},
              'boundary': 'Original job recovered through normal browser retry and independent durable backoff. A valid NO_CHANGE is not a publication or a new independent source. Foreground is a real local synthetic read fixture, not a repaired production service; overlap is not a latency benchmark.'}
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({'status': report['status'], 'checks': len(checks), 'failed': [k for k, v in checks.items() if not v]}))
    return all(checks.values())


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('before', 'after', 'authored', 'overlap', 'foreground', 'acceptance'):
        parser.add_argument('--' + name, required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    raise SystemExit(0 if verify({name: getattr(args, name) for name in ('before', 'after', 'authored', 'overlap', 'foreground', 'acceptance')}, args.output) else 1)
