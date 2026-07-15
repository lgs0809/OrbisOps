#!/usr/bin/env python3
"""Read-only evidence for immutable file projection and actual Run Skill budget reservations.
No model decisions or business successes are seeded. Supply a real browser Run ID after deployment.
"""
from pathlib import Path
import argparse, hashlib, json, re, runpy
ROOT = Path(__file__).resolve().parents[1]
PROJECT = 'ops-acceptance-a'


def inspect(output, run=None):
    s = runpy.run_path(str(ROOT / 'scripts/test-mcp-runtime.py'))
    q = s['quoted']; statements = []
    def rows(sql):
        statements.append(sql + ';')
        return s['rows'](sql)
    migrations = rows("SELECT JSON_OBJECT('version',version,'checksum',checksum) FROM orbisops_schema_history WHERE version IN ('085','086','087') ORDER BY version")
    files = rows("""SELECT JSON_OBJECT('scope',f.scope,'projectId',f.project_id,'skillId',f.skill_id,'version',f.projected_version,
        'sourceHash',f.source_hash,'skillHash',f.projected_skill_hash,'headVersion',s.current_version,'headHash',s.current_skill_hash,
        'packageHash',v.package_hash,'body',v.content,'manifest',v.manifest_json,'artifactHashes',v.artifact_hashes_json,'sourceType',v.source_type)
        FROM ai_ops_skill_file_projection f JOIN ai_ops_skill s ON s.scope=f.scope AND s.project_id=f.project_id AND s.skill_id=f.skill_id
        JOIN ai_ops_skill_version v ON v.scope=f.scope AND v.project_id=f.project_id AND v.skill_id=f.skill_id AND v.version=f.projected_version
        ORDER BY f.scope,f.project_id,f.skill_id""")
    pointers = rows("SELECT JSON_OBJECT('scope',scope,'projectId',project_id,'skillId',skill_id,'version',skill_version,'generation',generation_id) FROM ai_ops_skill_runtime_publication ORDER BY scope,project_id,skill_id")
    evidence = {'status': 'UNVERIFIED', 'migrations': migrations, 'files': files, 'runtimePointers': pointers,
                'modelInference': 'NOT_ASSESSED_BY_THIS_READ_ONLY_INSPECTOR', 'bodyBudgetUnit': 'UTF8_BYTE_UPPER_BOUND'}
    if run:
        if not re.fullmatch(r'chat-chat-session-[a-z0-9-]+', run):
            raise ValueError('Use the actual browser Run ID')
        evidence['run'] = rows("SELECT JSON_OBJECT('runId',run_id,'status',status,'projectId',project_id,'epoch',fencing_token) FROM ai_ops_agent_run WHERE run_id=" + q(run))
        evidence['loads'] = rows("SELECT JSON_OBJECT('skillKey',skill_key,'itemHash',item_hash,'units',units) FROM ai_ops_skill_runtime_budget_item WHERE project_id="+q(PROJECT)+" AND run_id="+q(run)+" ORDER BY item_hash")
        evidence['frozenBodies'] = rows("""SELECT JSON_OBJECT('skillId',r.skill_id,'version',r.version,'scope',r.scope,'packageHash',r.package_hash,'body',v.content)
            FROM ai_ops_runtime_context_bundle b JOIN JSON_TABLE(b.used_skill_version_refs_json, '$[*]' COLUMNS(
              skill_id VARCHAR(128) PATH '$.skillId',version INT PATH '$.version',scope VARCHAR(24) PATH '$.scope',package_hash VARCHAR(128) PATH '$.packageHash')) r
            JOIN ai_ops_skill_version v ON v.scope=r.scope AND v.project_id=IF(r.scope='GLOBAL','',b.project_id) AND v.skill_id=r.skill_id AND v.version=r.version
              AND v.package_hash=r.package_hash WHERE b.project_id="""+q(PROJECT)+" AND b.run_id="+q(run))
        evidence['runtimeReads'] = rows("SELECT JSON_OBJECT('sequence',sequence_no,'payload',payload_json) FROM ai_ops_agent_node_trace WHERE run_id="+q(run)+" AND event_type='TOOL_CALL_FINISHED' AND status='SUCCEEDED' AND JSON_UNQUOTE(JSON_EXTRACT(payload_json,'$.toolName')) IN ('UseProjectSkill','UseGlobalSkill') ORDER BY id")
        evidence['skillReceipts'] = rows("SELECT JSON_OBJECT('resultId',result_id,'projectId',project_id,'runId',run_id,'toolName',tool_name,'status',status,'outputHash',output_hash,'fullOutput',full_output) FROM ai_ops_tool_result WHERE run_id="+q(run)+" AND tool_name='skill_load' ORDER BY id")
        evidence['frozenCatalog'] = rows("SELECT JSON_OBJECT('bundle',bundle_json) FROM ai_ops_runtime_context_bundle WHERE run_id="+q(run))
    output.parent.mkdir(parents=True, exist_ok=True)
    output.with_name(output.stem+'-inspect.sql').write_text('\n'.join(statements)+'\n')
    output.with_name(output.stem+'-inspect.tsv').write_text(s['sql']('\n'.join(statements))+'\n')
    output.write_text(json.dumps(evidence, ensure_ascii=False, indent=2)+'\n')
    expected = {line.split('\t')[1]: line.split('\t')[4] for line in (ROOT/'server/db/migrations/manifest.tsv').read_text().splitlines() if line.startswith('orbisops\t')}
    assert len(migrations) == 3 and all(expected[m['version']] == m['checksum'] for m in migrations), migrations
    expected_files = {item['name'] for item in json.loads((ROOT/'scripts/fixtures/bundled-skill-routing.json').read_text())['skills']}
    actual_files = {item['skillId'] for item in files if item['scope'] == 'GLOBAL' and item['projectId'] == ''}
    assert expected_files <= actual_files, 'Configured bundled imports missing: ' + ', '.join(sorted(expected_files - actual_files))
    for f in files:
        assert f['version'] > 0 and f['sourceType'] == 'FILE_PROJECTION'
        assert f['headVersion'] == f['version'] and f['headHash'] == f['skillHash']
        hashes = json.loads(f['artifactHashes'])
        assert hashlib.sha256(f['body'].encode()).hexdigest() == hashes['SKILL.md']
        manifest = json.loads(f['manifest'])
        assert manifest['metadata']['version'] == f['version']
    if run:
        assert len(evidence['run']) == 1 and evidence['run'][0]['projectId'] == PROJECT
        loads = evidence['loads']; assert loads
        assert len({v['skillKey'] for v in loads}) <= 3 and sum(v['units'] for v in loads) <= 6000
        expected_loads = {}
        for body in evidence['frozenBodies']:
            identity = f"{body['scope']}:{PROJECT}:{body['skillId']}:{body['version']}:{body['packageHash']}"
            digest = hashlib.sha256((identity+'\n'+body['body']).encode()).hexdigest()
            expected_loads[digest] = {'skillKey': identity, 'itemHash': digest, 'units': len(body['body'].encode())}
        # A model can progressively load a catalog method and its resources even when automatic
        # applicability was unavailable. Verify its actual tool receipt against the frozen catalog
        # and immutable artifact hashes; do not equate a reservation alone with a successful read.
        catalog = json.loads(evidence['frozenCatalog'][0]['bundle'])['skillCatalogRefs']
        verified_reads = []
        for record in evidence['runtimeReads']:
            payload = json.loads(record['payload'])
            request = json.loads(payload['input'])
            if request.get('action') != 'load':
                continue
            # Trace output is intentionally capped for display. The immutable full receipt is the
            # authority, including its persisted hash and Run/project binding.
            receipt = next(v for v in evidence['skillReceipts'] if v['resultId'] == payload['resultId'])
            assert receipt['projectId'] == PROJECT and receipt['runId'] == run
            assert hashlib.sha256(receipt['fullOutput'].encode()).hexdigest() == receipt['outputHash']
            loaded = json.loads(receipt['fullOutput'])
            assert payload['allowed'] is True and receipt['status'] == loaded['status'] == 'SUCCEEDED'
            ref = next(v for v in catalog if v['skillId'] == loaded['skillId'])
            assert all(loaded[k] == ref[k] for k in ('version', 'skillHash', 'packageHash'))
            identity = f"{ref['scope']}:{PROJECT}:{ref['skillId']}:{ref['version']}:{ref['packageHash']}"
            contents = [('SKILL.md', loaded['content'], loaded['content'])]
            artifact = loaded.get('artifact')
            if artifact and artifact.get('encoding') == 'UTF8' and artifact['path'] != 'SKILL.md':
                contents.append((artifact['path'], artifact['content'], artifact['path']+'\n'+artifact['content']))
            for path, raw, budget_text in contents:
                assert hashlib.sha256(raw.encode()).hexdigest() == ref['artifactHashes'][path]
                digest = hashlib.sha256((identity+'\n'+budget_text).encode()).hexdigest()
                expected_loads[digest] = {'skillKey': identity, 'itemHash': digest, 'units': len(budget_text.encode())}
            verified_reads.append({'skillId': ref['skillId'], 'version': ref['version'], 'packageHash': ref['packageHash'],
                'paths': [v[0] for v in contents], 'resultId': receipt['resultId'], 'sequence': record['sequence']})
        evidence['verifiedRuntimeReads'] = verified_reads
        assert expected_loads and {v['itemHash']: v for v in loads} == expected_loads
    evidence['status'] = 'PASS'
    output.write_text(json.dumps(evidence, ensure_ascii=False, indent=2)+'\n')
    print(json.dumps({'status': 'PASS', 'fileVersions': len(files), 'pointerCount': len(pointers),
                      'run': run, 'runUnits': sum(v['units'] for v in evidence.get('loads', []))}))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--run')
    args = parser.parse_args(); inspect(args.output, args.run)
