#!/usr/bin/env python3
"""Upgrade exact, unedited bundled Skill copies in the isolated local acceptance stack.

Normal Skill update API records the configuration audit. Unknown bytes are preserved;
the script never deletes files, changes governance, seeds approvals or marks tasks successful.
"""
from pathlib import Path
import argparse
import hashlib
import json
import re
import runpy
import subprocess

ROOT = Path(__file__).resolve().parents[1]
FIXTURE = ROOT / 'scripts/fixtures/bundled-skill-routing.json'


def digest(value):
    return hashlib.sha256(value).hexdigest()


def decision(current, source, legacy):
    if current == source:
        return 'ALREADY_CURRENT'
    if current in legacy:
        return 'UPDATE_BUNDLED_COPY'
    return 'PRESERVED_USER_CONTENT'


def seed(output, dry_run=False):
    helper = runpy.run_path(str(ROOT / 'scripts/test-mcp-runtime.py'))
    records = []
    def save(status):
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps({'status': status, 'dryRun': dry_run,
            'concurrencyBoundary': 'Exact-byte checks before API update; do not edit the same file concurrently.',
            'skills': records}, ensure_ascii=False, indent=2) + '\n')
    save('IN_PROGRESS')
    for item in json.loads(FIXTURE.read_text())['skills']:
        name = item['name']
        if not re.fullmatch(r'[a-z0-9-]+', name):
            raise ValueError('Invalid bundled Skill name')
        source = (ROOT / 'server/orbisops-app/src/main/resources/skills' / name / 'SKILL.md').read_bytes()
        assert digest(source) == item['sourceSha256'], 'Bundled fixture source changed; review its provenance first'
        command = ['docker', 'exec', 'orbisops-acceptance-backend-1', 'cat', '/opt/orbisops/data/skills/' + name + '/SKILL.md']
        current = subprocess.run(command, capture_output=True, check=False)
        if current.returncode:
            records.append({'name': name, 'status': 'PRESERVED_MISSING_OR_UNREADABLE'})
            save('IN_PROGRESS')
            continue
        before = digest(current.stdout)
        action = decision(before, item['sourceSha256'], item['legacySha256'])
        audit_sql = "SELECT JSON_OBJECT('id',id,'action',action_name,'targetId',target_id,'result',result_status) FROM ai_ops_config_audit WHERE module_name='skill' AND action_name='update' AND target_id=" + helper['quoted'](name) + ' ORDER BY id'
        audits_before = helper['rows'](audit_sql)
        record = {'name': name, 'beforeSha256': before, 'sourceSha256': item['sourceSha256'], 'status': action,
                  'auditCountBefore': len(audits_before), 'auditSql': audit_sql + ';'}
        records.append(record)
        save('IN_PROGRESS')
        if action == 'UPDATE_BUNDLED_COPY' and not dry_run:
            # A second read narrows the race; the API has no cross-process filesystem CAS contract.
            assert digest(subprocess.run(command, capture_output=True, check=True).stdout) == before, 'Concurrent file edit; retained'
            helper['api']('/api/v1/admin/ops/skills/' + name, 'PUT', {'content': source.decode()}, helper['token'])
            after = subprocess.run(command, capture_output=True, check=True).stdout
            assert digest(after) == item['sourceSha256'], 'Updated bytes differ; inspect normal API result'
            record['afterSha256'] = digest(after)
            record['status'] = 'UPDATED_THROUGH_API'
        audits_after = helper['rows'](audit_sql)
        added = [a for a in audits_after if a['id'] not in {b['id'] for b in audits_before}]
        record['auditCountAfter'] = len(audits_after)
        record['newAudits'] = added
        assert len(added) == (1 if record['status'] == 'UPDATED_THROUGH_API' else 0), 'Unexpected file update audit count'
        save('IN_PROGRESS')
    preserved = [r['name'] for r in records if r['status'].startswith('PRESERVED_')]
    save('PASS_WITH_PRESERVED_FILES' if preserved else 'PASS')
    print(json.dumps({'status': 'PASS_WITH_PRESERVED_FILES' if preserved else 'PASS', 'skills': len(records),
        'updated': sum(r['status'] == 'UPDATED_THROUGH_API' for r in records), 'preserved': preserved, 'dryRun': dry_run}))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--dry-run', action='store_true')
    args = parser.parse_args()
    seed(args.output, args.dry_run)
