#!/usr/bin/env python3
"""Bounded local traffic to build actual time series and linked logs; does not assert business PASS."""
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import argparse
import collections
import datetime
import json
import signal
import threading
import time
import urllib.error
import urllib.request

parser = argparse.ArgumentParser()
parser.add_argument("--duration-seconds", type=int, default=1800)
parser.add_argument("--output", type=Path, required=True)
args = parser.parse_args()
assert 1 <= args.duration_seconds <= 3600
if args.output.exists():
    parser.error("Preserve previous traffic evidence; choose a new output")
started = time.monotonic()
started_epoch = time.time()
deadline = started + args.duration_seconds
lock = threading.Lock()
stopping = threading.Event()
for stop_signal in (signal.SIGINT, signal.SIGTERM):
    signal.signal(stop_signal, lambda _signal, _frame: stopping.set())
counts = collections.defaultdict(collections.Counter)
targets = [("healthy", "ops-acc-a-order-1", 1), ("errors", "ops-acc-a-order-2", 1),
           ("insufficient", "ops-acc-a-order-3", 60), ("slow-sql", "ops04-slow-order-1", 1)]


def snapshot(final=False):
    with lock:
        data = {"fixture": "OPS-04-real-HTTP-load-synthetic-orders", "elapsedSeconds": round(time.monotonic() - started, 1),
                "startedAt": datetime.datetime.fromtimestamp(started_epoch,datetime.timezone.utc).isoformat(),
                "startEpoch": started_epoch, "plannedEndEpoch": started_epoch + args.duration_seconds,
                "snapshotAt": datetime.datetime.now(datetime.timezone.utc).isoformat(),
                "complete": final and not stopping.is_set() and time.monotonic() >= deadline,
                "stopped": stopping.is_set(),
                "requests": {key: dict(value) for key, value in counts.items()},
                "scope": "Traffic evidence only; health and workflow assertions are separate"}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n")
    return data


def generate(target):
    name, order, interval = target
    while not stopping.is_set() and time.monotonic() < deadline:
        began = time.monotonic()
        try:
            with urllib.request.urlopen("http://127.0.0.1:18262/orders/" + order, timeout=5) as response:
                response.read()
                status = str(response.status)
        except urllib.error.HTTPError as error:
            error.read()
            status = str(error.code)
        except OSError:
            status = "transport-failure"
        with lock:
            counts[name][status] += 1
        delay = min(deadline - time.monotonic(), interval - (time.monotonic() - began))
        if delay > 0:
            stopping.wait(delay)


with ThreadPoolExecutor(max_workers=4) as pool:
    futures = [pool.submit(generate, target) for target in targets]
    while any(not future.done() for future in futures):
        snapshot()
        if stopping.wait(min(5, max(0.1, deadline - time.monotonic()))):
            break
    for future in futures:
        future.result()
print(json.dumps(snapshot(True), ensure_ascii=False))
