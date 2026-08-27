#!/usr/bin/env python3
"""Export a read-only inventory of the existing local acceptance environment.

No API login, model call, seeding, or mutation. Credentials stay inside MySQL's
existing container. The companion SQL is also usable in a database client.
"""
import argparse
import datetime
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
SQL = ROOT / 'scripts/fixtures/ops08-environment-inspect.sql'


def main(output):
    # Keep both artifacts: an existing manual snapshot is never overwritten.
    if output.exists() or output.with_suffix('.sql').exists():
        raise SystemExit('Choose a new output path; existing evidence is preserved.')
    query = SQL.read_text()
    result = subprocess.run(
        ['docker', 'exec', '-i', 'orbisops-acceptance-mysql-1', 'sh', '-c',
         'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --default-character-set=utf8mb4 '
         '-uroot -N -B --raw orbisops_acceptance'],
        input=query, text=True, capture_output=True, timeout=30)
    if result.returncode:
        raise SystemExit('Read-only inventory failed; check the local MySQL container and migrations.')
    facts = [json.loads(line) for line in result.stdout.splitlines() if line.strip()]
    proof = {'capturedAt': datetime.datetime.now(datetime.timezone.utc).isoformat(),
             'scope': 'LOCAL_ACCEPTANCE_INVENTORY_NOT_TEST_VERDICT', 'readOnly': True,
             'facts': facts}
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.with_suffix('.sql').open('x') as stream:
        stream.write(query)
    with output.open('x') as stream:
        json.dump(proof, stream, ensure_ascii=False, indent=2)
        stream.write('\n')
    print(json.dumps({'output': str(output), 'facts': len(facts), 'readOnly': True}))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.output.suffix != '.json':
        parser.error('--output must end with .json')
    main(args.output)
