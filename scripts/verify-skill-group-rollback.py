#!/usr/bin/env python3
"""Assess persisted group rollback and subsequent actual Skill reads; never changes business state."""
import argparse
import hashlib
import json
from pathlib import Path


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
        'original_body_and_resource_actually_read': any(r['skillId'] in {m['skillId'] for m in sources}
            and 'SKILL.md' in r['paths'] and any(p != 'SKILL.md' for p in r['paths']) for r in reads),
    }
    result = {'status': 'PASS' if all(checks.values()) else 'FAIL', 'checks': checks,
        'candidateId': args.candidate, 'runId': use['run'][0]['runId'],
        'inputSha256': {str(p.resolve()): hashlib.sha256(p.read_bytes()).hexdigest() for p in (args.publication, args.use)},
        'boundary': 'Browser rollback and post-rollback query assessed from retained read-only facts. This is not SPLIT or model-quality acceptance.'}
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps(result, ensure_ascii=False))
    raise SystemExit(0 if result['status'] == 'PASS' else 1)


if __name__ == '__main__':
    main()
