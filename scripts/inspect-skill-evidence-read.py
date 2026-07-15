#!/usr/bin/env python3
"""Read-only frozen source / actual model read audit verification; never manufactures an experience."""
import argparse
import json
import os
from pathlib import Path
import runpy
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def inspect(source_id, output):
    if output.exists() or output.with_suffix('.sql').exists():
        raise ValueError('Choose a new evidence filename')
    h = runpy.run_path(str(ROOT / 'scripts/test-mcp-runtime.py'))
    source = h['quoted'](source_id)
    queries = {
        'source': "SELECT JSON_OBJECT('hash',input_hash,'actualHash',SHA2(input_json,256),'bytes',OCTET_LENGTH(input_json),'body',input_json) FROM ai_ops_skill_evolution_source WHERE project_id='ops-acceptance-a' AND source_id=" + source,
        'job': "SELECT JSON_OBJECT('jobId',j.job_id,'status',j.status,'attempts',j.attempts,'error',j.last_error) FROM ai_ops_skill_evolution_job j JOIN ai_ops_skill_evolution_job_state s ON s.job_id=j.job_id WHERE j.project_id='ops-acceptance-a' AND s.source_id=" + source,
        'fact': "SELECT JSON_OBJECT('sourceHash',f.source_hash,'methodHash',f.method_hash,'actualMethodHash',SHA2(f.method_json,256),'method',CAST(f.method_json AS JSON),'audit',CAST(f.extraction_audit AS JSON),'groupId',f.group_id,'episodeRevision',f.episode_revision,'currentEpisodeRevision',e.revision) FROM ai_ops_skill_method_experience f JOIN ai_ops_task_episode e ON e.project_id=f.project_id AND e.episode_id=f.episode_id WHERE f.project_id='ops-acceptance-a' AND f.source_id=" + source,
    }
    data = {name: h['rows'](sql) for name, sql in queries.items()}
    assert len(data['source']) == 1
    frozen = data['source'][0]
    raw = frozen.pop('body')
    result = {'status': 'NOT_COMPLETED', 'sourceId': source_id, **data,
              'boundary': 'Read provenance and immutable hashes only. Method correctness and publication are separate checks.'}
    output.parent.mkdir(parents=True, exist_ok=True)
    output.with_suffix('.sql').write_text(';\n'.join(queries.values())+';\n')
    try:
        assert frozen['hash'] == frozen['actualHash']
        if data['fact']:
            assert len(data['fact']) == 1
            fact = data['fact'][0]
            assert fact['sourceHash'] == frozen['hash']
            assert fact['methodHash'] == fact['actualMethodHash']
            assert fact['episodeRevision'] == fact['currentEpisodeRevision']
            audit = fact['audit']
            assert audit['authoringModel'] == 'gpt-5.6-terra'
            assert audit['modelInputEncoding'] == 'ops-evidence-directory-v1'
            assert audit['evidenceSufficient'] is True
            reads = audit['evidenceInputAudit']
            assert reads['completeSourceReviewed'] is False
            assert 1 <= len(reads['requestHashes']) <= reads.get('readRoundsLimit', 3) + 1 <= 6
            assert audit['evidenceBasis']
            assert set(audit['evidenceBasis']) <= {r['id'] for r in reads['readEvidence']}
            java = str(Path(os.environ['JAVA_HOME']) / 'bin/java') if os.environ.get('JAVA_HOME') else 'java'
            verified = subprocess.run([java, '--class-path', str(ROOT/'server/orbisops-domain/target/classes'),
                                      str(ROOT/'scripts/VerifySkillEvidenceAudit.java')],
                                     input=json.dumps({'source': raw, 'audit': reads}, ensure_ascii=False),
                                     text=True, capture_output=True, timeout=30, check=True)
            result['readVerification'] = json.loads(verified.stdout)
            result['status'] = 'PASS_READ_PROVENANCE_ONLY'
    except Exception as error:
        result['status'] = 'FAIL'
        result['failureType'] = type(error).__name__
        raise
    finally:
        output.write_text(json.dumps(result, ensure_ascii=False, indent=2)+'\n')
    print(json.dumps({'status': result['status'], 'sourceBytes': frozen['bytes'], 'jobs': data['job'],
                      'verifiedReads': len(result.get('readVerification', {}).get('verifiedReads', []))}))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source-id', required=True)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    inspect(args.source_id, args.output)
