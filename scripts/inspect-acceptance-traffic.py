#!/usr/bin/env python3
"""Cross-check a retained bounded HTTP traffic file with native business request rows.

Read-only: never creates requests, metrics, timestamps, approval or an SLO verdict.
Use an existing file from generate-acceptance-traffic.py and a fresh evidence path.
"""
import argparse
import datetime as dt
import hashlib
import json
from pathlib import Path
import re
import runpy

ROOT = Path(__file__).resolve().parents[1]


def inspect(source, output):
    if output.exists() or output.with_suffix('.sql').exists():
        raise ValueError('Preserve earlier evidence; choose a fresh output')
    traffic = json.loads(source.read_text())
    records = traffic['records']
    if not traffic.get('actualHttpRequests') or not traffic.get('syntheticOrders') or not records:
        raise ValueError('An actual completed bounded traffic file is required')
    ids = [record.get('traceId', '') for record in records]
    if any(not re.fullmatch(r'[a-f0-9-]{36}', value) for value in ids):
        raise ValueError('Every request must retain its real UUID trace identity')
    h = runpy.run_path(str(ROOT / 'scripts/test-mcp-runtime.py'))
    queries, rows = [], []
    for offset in range(0, len(ids), 200):
        selected = ','.join(h['quoted'](value) for value in ids[offset:offset + 200])
        query = ("SELECT JSON_OBJECT('traceId',event_id,'serviceId',service_id,'orderId',order_id,"
                 "'startedAt',observed_at,'status',http_status,'durationMs',duration_ms,'version',version) "
                 "FROM ops_acceptance_business_a.ops04_request WHERE event_id IN (" + selected + ')')
        queries.append(query)
        rows.extend(h['rows'](query))
    native = {row['traceId']: row for row in rows}
    checks = {
        'allTraceIdsUnique': len(set(ids)) == len(ids),
        'everyActualRequestRetained': len(rows) == len(records) and set(native) == set(ids),
        'responseMatchesNativeRecord': all(value in native and all(
            record.get(field) == native[value][field] for field in ('serviceId', 'orderId', 'status', 'version'))
            for value, record in zip(ids, records)),
        'everyNativeDurationNonnegative': all(row['durationMs'] >= 0 for row in rows),
        'requestedServiceMatches': bool(traffic.get('expectedService')) and all(
            row['serviceId'] == traffic['expectedService'] for row in rows),
        'noTargetValidationFailures': not any(record.get('validationError') for record in records),
    }
    parse = lambda value: dt.datetime.fromisoformat(value).replace(tzinfo=dt.timezone.utc)
    completed = sorted(parse(row['startedAt']) + dt.timedelta(milliseconds=row['durationMs']) for row in rows)
    gaps = [(right - left).total_seconds() for left, right in zip(completed, completed[1:])]
    result = {
        'status': 'PASS_NATIVE_TRAFFIC_INTEGRITY' if all(checks.values()) else 'FAIL_INTEGRITY',
        'observedAt': dt.datetime.now(dt.timezone.utc).isoformat(),
        'inputFile': str(source), 'inputSha256': hashlib.sha256(source.read_bytes()).hexdigest(),
        'checks': checks, 'targetQps': traffic.get('targetQps'), 'actualRequestCount': len(rows),
        'httpErrors': sum(row['status'] >= 400 for row in rows),
        'versions': sorted({row['version'] for row in rows}),
        'firstCompletedAt': completed[0].isoformat() if completed else None,
        'lastCompletedAt': completed[-1].isoformat() if completed else None,
        'maximumCompletionGapSeconds': max(gaps, default=0),
        'nativeRequests': rows,
        'boundary': 'Actual isolated HTTP and MySQL integrity; approved SLO windows and task acceptance require independent verification.',
    }
    output.parent.mkdir(parents=True, exist_ok=True)
    output.with_suffix('.sql').write_text('START TRANSACTION READ ONLY;\n' + ';\n'.join(queries) + ';\nCOMMIT;\n')
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({key: result[key] for key in ('status', 'actualRequestCount', 'httpErrors', 'versions', 'maximumCompletionGapSeconds')}))
    if not all(checks.values()):
        raise SystemExit(1)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--input', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    arguments = parser.parse_args()
    inspect(arguments.input, arguments.output)
