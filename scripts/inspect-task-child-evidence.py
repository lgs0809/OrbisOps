#!/usr/bin/env python3
"""Read actual child evidence through the deployed task-scope implementation, without writing task state."""
import argparse
import json
from pathlib import Path
import re
from deployed_repository_probe import run_probe


def inspect(episode, output, verify_accepted=False):
    if not re.fullmatch(r'task-episode-[a-z0-9-]+', episode):
        raise ValueError('An actual task episode ID is required')
    if output.exists() or output.with_suffix('.sql').exists():
        raise ValueError('Preserve previous evidence; choose a new path')
    output.parent.mkdir(parents=True, exist_ok=True)
    output.with_suffix('.sql').write_text(f"""-- Read-only roots; deployed Java also verifies canonical checkpoints, child identities and receipt hashes.
START TRANSACTION READ ONLY;
SELECT source_run_ref,episode_revision FROM ai_ops_task_episode_turn
WHERE project_id='ops-acceptance-a' AND episode_id='{episode}' AND status='ASSIGNED';
ROLLBACK;
""")
    result = {'status': 'FAIL', 'episodeId': episode}
    try:
        result.update(run_probe('InspectTaskChildEvidence', json.dumps({'episodeId': episode, 'verifyAccepted': verify_accepted})))
        if verify_accepted:
            output.with_suffix('.sql').write_text(output.with_suffix('.sql').read_text()+f"""
-- Browser acceptance and its full frozen source. These statements never change business state.
START TRANSACTION READ ONLY;
SELECT acceptance_id,episode_revision,outcome,source_run_id,record_hash,SHA2(record_json,256) AS actual_hash,
       JSON_LENGTH(JSON_EXTRACT(record_json,'$.checks')) AS check_count
FROM ai_ops_task_acceptance WHERE project_id='ops-acceptance-a' AND episode_id='{episode}';
SELECT source_id,input_hash,SHA2(input_json,256) AS actual_hash,
       JSON_LENGTH(JSON_EXTRACT(input_json,'$.evidenceRunScope')) AS scoped_runs,
       JSON_LENGTH(JSON_EXTRACT(input_json,'$.receipts')) AS full_receipts
FROM ai_ops_skill_evolution_source WHERE project_id='ops-acceptance-a' AND episode_id='{episode}';
ROLLBACK;
""")
    except Exception as failure:
        result['failureType'] = type(failure).__name__
        result['diagnostic'] = str(failure)[:2500]
        raise
    finally:
        output.write_text(json.dumps(result, ensure_ascii=False, indent=2)+'\n')
    print(json.dumps({'status': result['status'], 'runs': len(result['runs']), 'receipts': len(result['receipts'])}))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--episode-id', required=True)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--verify-accepted', action='store_true', help='Require an actual current acceptance and an already frozen parent/child source')
    args = parser.parse_args()
    inspect(args.episode_id, args.output, args.verify_accepted)
