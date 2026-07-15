#!/usr/bin/env python3
"""Read-only cross-store evidence for the API-seeded synthetic compression fixture."""
import argparse
import hashlib
import json
from pathlib import Path
import runpy
import subprocess

ROOT = Path(__file__).resolve().parents[1]
PROJECT = 'ops-maintenance-acceptance'
SKILL = 'synthetic-adjacent-prose-maintenance'


def inspect(output, semantic=False, expect_rollback=False):
    if output.exists() or output.with_suffix('.sql').exists():
        raise ValueError('Choose a new evidence filename; retain earlier results')
    support = runpy.run_path(str(ROOT / 'scripts/test-mcp-runtime.py'))
    fixture = runpy.run_path(str(ROOT / 'scripts/seed-skill-maintenance.py'))
    source = fixture['semantic_source' if semantic else 'source']()
    scope = " WHERE project_id='" + PROJECT + "' AND skill_id='" + source['skillId'] + "'"
    queries = {
        'checks': "SELECT JSON_OBJECT('id',check_id,'base',base_version,'kind',kind,'status',status,'reason',reason,'tokens',body_tokens,'tokenizer',tokenizer,'attempts',attempts,'published',published_version,'review',evidence_json) FROM ai_ops_skill_maintenance" + scope,
        'versions': "SELECT JSON_OBJECT('version',version,'skillHash',skill_hash,'packageHash',package_hash,'bodyHash',SHA2(content,256),'bodyBytes',OCTET_LENGTH(content),'artifacts',artifact_hashes_json,'routing',JSON_EXTRACT(manifest_json,'$.routingProfile')) FROM ai_ops_skill_version" + scope + ' ORDER BY version',
        'head': "SELECT JSON_OBJECT('version',current_version,'skillHash',current_skill_hash,'packageHash',current_package_hash,'lockType',lock_type,'status',status,'updateMode',update_mode) FROM ai_ops_skill" + scope,
        'artifacts': "SELECT JSON_OBJECT('version',version,'path',artifact_path,'hash',content_hash,'content',content) FROM ai_ops_skill_artifact" + scope + ' ORDER BY version, artifact_path',
        'publication': "SELECT JSON_OBJECT('version',skill_version,'skillHash',skill_hash,'packageHash',package_hash,'generation',generation_id) FROM ai_ops_skill_runtime_publication" + scope,
    }
    data = {name: support['rows'](query) for name, query in queries.items()}
    pg_query = "SELECT row_to_json(e.*) FROM (SELECT g.generation_id,g.skill_version,g.skill_hash,g.package_hash,g.status,g.dimension,d.content_hash,vector_dims(d.embedding) AS actual_dimensions FROM ops_skill_route_generation g LEFT JOIN ops_skill_route_document d USING(generation_id)" + scope + ') e'
    raw = subprocess.run(['docker', 'exec', '-i', 'orbisops-acceptance-pgvector-1', 'sh', '-c',
        'exec psql -X -A -t -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'],
        input=pg_query, text=True, capture_output=True, check=True).stdout
    data['index'] = [json.loads(line) for line in raw.splitlines() if line]
    versions = {v['version']: v for v in data['versions']}
    original, compressed = versions.get(1), versions.get(2)
    checks = {}
    if original and compressed:
        resources = lambda v: {k: value for k, value in json.loads(v['artifacts']).items() if k != 'SKILL.md'}
        expected = {a['path']: hashlib.sha256(a['content'].encode()).hexdigest() for a in source['artifacts']}
        checks['originalInputUnchanged'] = original['bodyHash'] == hashlib.sha256(source['content'].encode()).hexdigest() and resources(original) == expected and all(hashlib.sha256(a['content'].encode()).hexdigest() == a['hash'] for a in data['artifacts'])
        if semantic:
            content = lambda v, p: next(a['content'] for a in data['artifacts'] if a['version'] == v and a['path'] == p)
            before, after = content(1, 'resources/method.json'), content(2, 'resources/method.json')
            checks['referencedMethodSmaller'] = len(after.encode()) < len(before.encode())
            checks['entryAndBoundaryUnchanged'] = original['bodyHash'] == compressed['bodyHash'] and resources(original)['resources/boundary.json'] == resources(compressed)['resources/boundary.json']
            checks['semanticProposalProvenance'] = any((review := json.loads(c.get('review') or '{}')).get('equivalent') is True
                and (proposal := review.get('proposal', {})).get('authoringModel') == 'gpt-5.6-terra'
                and proposal.get('policy') == 'semantic-maintenance-v1' and proposal.get('sourceInputHash')
                and proposal.get('authoringBindingHash') and any(p.get('path') == 'resources/method.json' for p in proposal.get('changes', [])) for c in data['checks'])
        else:
            checks['compressedBodySmaller'] = compressed['bodyBytes'] < original['bodyBytes']
            checks['resourceHashesUnchanged'] = resources(original) == resources(compressed) and bool(resources(original))
        checks['realReviewAccepted'] = any((review := json.loads(c.get('review') or '{}')).get('equivalent') is True
            and review.get('safe') is True and review.get('authoringModel') == 'gpt-5.6-terra'
            and review.get('authoringBindingHash') and review.get('sourceInputHash') for c in data['checks'])
        checks['compressedIndexReady'] = any(g['skill_version'] == 2 and g['status'] == 'READY'
            and g['actual_dimensions'] == 1024 and g['package_hash'] == compressed['packageHash'] for g in data['index'])
    head = data['head'][0] if data['head'] else {}
    checks['pointerMatchesHead'] = bool(data['publication']) and all(all(p[k] == head.get(k)
        for k in ('version', 'skillHash', 'packageHash')) for p in data['publication'])
    if expect_rollback:
        restored = versions.get(head.get('version'), {})
        checks['rollbackRestoresWholePackage'] = head.get('version', 0) > 2 and original is not None and restored.get('bodyHash') == original['bodyHash'] and json.loads(restored.get('artifacts', '{}')) == json.loads(original['artifacts']) and restored.get('routing') == original.get('routing') and bool(original.get('routing'))
        checks['rollbackIndexReady'] = any(g['skill_version'] == head.get('version') and g['status'] == 'READY' and g['package_hash'] == head.get('packageHash') for g in data['index'])
    data['checksPassed'] = checks
    expected_checks = 6 + int(semantic) + 2 * int(expect_rollback)
    data['status'] = 'PASS_SYNTHETIC_MAINTENANCE' if len(checks) == expected_checks and all(checks.values()) else 'NOT_PASSED'
    data['boundary'] = 'Synthetic API-created input; real Terra review, version publication and MySQL/PG state. Not business quality or all-Skill acceptance.'
    output.parent.mkdir(parents=True, exist_ok=True)
    output.with_suffix('.sql').write_text('-- MySQL\n' + ';\n'.join(queries.values()) + ';\n-- PostgreSQL\n' + pg_query + ';\n')
    output.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({'status': data['status'], 'checks': checks, 'head': head}, ensure_ascii=False))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--semantic', action='store_true')
    parser.add_argument('--expect-rollback', action='store_true')
    args = parser.parse_args()
    inspect(args.output, args.semantic, args.expect_rollback)
