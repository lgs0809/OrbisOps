#!/usr/bin/env python3
"""Assess persisted group rollback and subsequent actual Skill reads; never changes business state."""
import argparse
import hashlib
import json
from pathlib import Path


def original_package_actually_read(sources, catalogs, reads, injected):
    """Require the original full body and every declared file; a body-only package needs no invented resource."""
    for source in sources:
        refs=[ref for bundle in catalogs for ref in bundle.get('skillCatalogRefs', [])
              if ref.get('skillId')==source['skillId'] and ref.get('version')==source['version']
              and ref.get('packageHash')==source['packageHash']]
        if len(refs)!=1 or not isinstance(refs[0].get('artifactHashes'),dict):
            continue
        expected=set(refs[0]['artifactHashes'])
        if 'SKILL.md' not in expected:
            continue
        observed=set()
        for read in reads:
            if (read.get('skillId'),read.get('version'),read.get('packageHash'))==(source['skillId'],source['version'],source['packageHash']):
                observed.update(read.get('paths', []))
        for body in injected:
            if (body.get('skillId'),body.get('version'),body.get('packageHash'))==(source['skillId'],source['version'],source['packageHash']) and isinstance(body.get('body'),str):
                if hashlib.sha256(body['body'].encode()).hexdigest()==refs[0]['artifactHashes']['SKILL.md']:
                    observed.add('SKILL.md')
        if expected.issubset(observed):
            return True
    return False


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--publication', required=True, type=Path)
    parser.add_argument('--use', required=True, type=Path)
    parser.add_argument('--candidate', required=True)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    if args.output.exists():
        raise ValueError('Retain earlier evidence; choose a fresh output')
    publication = json.loads(args.publication.read_bytes())
    use = json.loads(args.use.read_bytes())
    groups = [g for g in publication['atomicGroups'] if g['candidateId'] == args.candidate]
    releases = [r for r in publication['releases'] if r['candidateId'] == args.candidate]
    members = [m for m in publication['atomicMembers'] if m['candidateId'] == args.candidate]
    group = groups[0] if len(groups) == 1 else {}
    sources = [m for m in members if m['role'] == 'SOURCE']
    targets = [m for m in members if m['role'] == 'TARGET']
    catalogs = [json.loads(row['bundle']) for row in use.get('frozenCatalog', [])]
    catalog_ids = {ref['skillId'] for bundle in catalogs for ref in bundle.get('skillCatalogRefs', [])}
    reads = use.get('verifiedRuntimeReads', [])
    checks = {
        'publication_snapshot_consistent': not publication['mismatches'],
        'group_and_release_rolled_back': len(groups) == len(releases) == 1 and group.get('status') == releases[0]['status'] == 'ROLLED_BACK',
        'actor_and_reason_preserved': bool(group.get('rollbackActor') and group.get('rollbackReason')),
        'all_immutable_members_retained': len(sources) == len(group.get('plan', {}).get('sources', [])) > 0
            and len(targets) == len(group.get('plan', {}).get('targets', [])) > 0
            and all(m['bodyHash'] == m['storedBodyHash'] and m['skillHash'] == m['versionSkillHash']
                and m['packageHash'] == m['versionPackageHash'] for m in members),
        'original_routing_restored': all(m['replacedBy'] != args.candidate for m in sources),
        'new_run_succeeded': use['status'] == 'PASS' and len(use['run']) == 1 and use['run'][0]['status'] == 'SUCCEEDED',
        'new_catalog_contains_originals': all(m['skillId'] in catalog_ids for m in sources),
        'new_catalog_excludes_rolled_back_targets': all(m['skillId'] not in catalog_ids for m in targets),
        'original_body_and_all_declared_files_actually_read': original_package_actually_read(
            sources,catalogs,reads,use.get('frozenBodies', [])),
    }
    result = {'status': 'PASS' if all(checks.values()) else 'FAIL', 'checks': checks,
        'candidateId': args.candidate, 'runId': use['run'][0]['runId'],
        'inputSha256': {str(p.resolve()): hashlib.sha256(p.read_bytes()).hexdigest() for p in (args.publication, args.use)},
        'operation': group.get('operation'),
        'boundary': 'Browser group rollback and a separate post-rollback successful query assessed from native facts. Covers the recorded operation only; publication, earlier reuse and model quality require their own evidence.'}
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps(result, ensure_ascii=False))
    raise SystemExit(0 if result['status'] == 'PASS' else 1)


if __name__ == '__main__':
    main()
