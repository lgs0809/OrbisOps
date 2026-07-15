#!/usr/bin/env python3
"""Generate bounded real HTTP order reads on the isolated target; preserve trace IDs for SQL checks."""
import argparse
import concurrent.futures
import datetime
import json
from pathlib import Path
import re
import time
import urllib.error
import urllib.request


def run(order, seconds, interval, expected_service=None):
    end = time.monotonic() + seconds
    records = []
    while time.monotonic() < end:
        started = time.monotonic()
        record = {'orderId': order, 'at': datetime.datetime.now(datetime.timezone.utc).isoformat()}
        try:
            try:
                response = urllib.request.urlopen('http://127.0.0.1:18262/orders/' + order, timeout=5)
            except urllib.error.HTTPError as error:
                response = error
            with response:
                record.update(status=response.status, traceId=response.headers.get('X-Trace-Id'))
                payload = json.load(response)
                actual = payload.get('order', {})
                record.update(serviceId=actual.get('serviceId'), scenario=actual.get('scenario'), version=actual.get('version'))
                if expected_service and record['serviceId'] != expected_service:
                    record['validationError'] = 'TARGET_SERVICE_MISMATCH'
                    records.append(record)
                    break
        except OSError as error:
            record.update(status='TRANSPORT_ERROR', errorType=type(error).__name__)
        records.append(record)
        time.sleep(max(0, min(end - time.monotonic(), interval - (time.monotonic() - started))))
    return records


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--seconds', type=int, default=300)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--qps', type=float, default=2.0, help='Total target load across selected order readers (0.5..2)')
    parser.add_argument('--orders', type=int, nargs='+', choices=(1, 2, 3, 4), default=[1, 2],
                        help='Literal ops-acc-a-order-N IDs, NOT service numbers; requests remain read-only')
    parser.add_argument('--order-id', help='Exact isolated order ID, overrides --orders; e.g. ops04-slow-order-1')
    parser.add_argument('--expect-service', help='Assert the actual order belongs to this service; stop on mismatch')
    args = parser.parse_args()
    if not 1 <= args.seconds <= 1800:
        parser.error('seconds must be 1..1800; no unbounded background traffic')
    if not 0.5 <= args.qps <= 2:
        parser.error('qps must be 0.5..2')
    if args.order_id and not re.fullmatch(r'(?:ops-acc-a-order-|ops04-slow-order-)\d+', args.order_id):
        parser.error('Only existing isolated fixture order IDs are supported')
    if args.expect_service and not re.fullmatch(r'ops-acc-a-service-[1-4]', args.expect_service):
        parser.error('Only isolated fixture services are supported')
    if args.output.exists():
        parser.error('Preserve existing evidence; choose a new output path')
    orders = [args.order_id] if args.order_id else ['ops-acc-a-order-' + str(i) for i in sorted(set(args.orders))]
    with concurrent.futures.ThreadPoolExecutor(max_workers=len(orders)) as pool:
        futures = [pool.submit(run, order, args.seconds, len(orders) / args.qps, args.expect_service) for order in orders]
        records = [row for future in futures for row in future.result()]
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps({'syntheticOrders': True, 'actualHttpRequests': True, 'targetQps': args.qps, 'orders': orders,
                                      'expectedService': args.expect_service, 'records': records}, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({'requests': len(records), 'withTraceId': sum(bool(r.get('traceId')) for r in records)}))
    if any(r.get('validationError') for r in records):
        raise SystemExit('Actual request target mismatches the requested service; evidence retained')
