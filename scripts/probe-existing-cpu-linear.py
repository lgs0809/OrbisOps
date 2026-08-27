#!/usr/bin/env python3
"""Native CPU linear-kernel diagnostic only; no models, weights or service changes."""
import argparse
import json
import time
from datetime import datetime, timezone
from pathlib import Path
import torch
import torch.nn.functional as functional

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
if args.output.exists():
    raise ValueError('Use a fresh report')
torch.set_num_threads(2)
torch.manual_seed(79)
report = {'status': 'RUNNING', 'startedAt': datetime.now(timezone.utc).isoformat(),
          'torchVersion': torch.__version__, 'threads': torch.get_num_threads(),
          'scope': 'SMALL_NATIVE_LINEAR_DIAGNOSTIC_NOT_FULL_QWEN_INFERENCE', 'results': [],
          'boundary': 'Synthetic matrices only. Does not change the service, model precision, '
              'weights, configuration, business records or semantic inputs. No quality/performance '
              'claim for whole model follows from this operator comparison.'}

def save():
    args.output.write_text(json.dumps(report, indent=2) + '\n')

try:
    for rows in (128, 512, 2754):
        source = torch.randn(rows, 2048).to(torch.bfloat16)
        weight = torch.randn(6144, 2048).to(torch.bfloat16)
        outputs = []
        for dtype in (torch.bfloat16, torch.float32):
            x, w = source.to(dtype), weight.to(dtype)
            start = time.monotonic()
            with torch.inference_mode():
                value = functional.linear(x, w)
            seconds = time.monotonic() - start
            result = {'rows': rows, 'inputWidth': 2048, 'outputWidth': 6144,
                      'dtype': str(dtype), 'seconds': seconds, 'finite': bool(torch.isfinite(value).all())}
            outputs.append(value[:2, :16].float())
            report['results'].append(result)
            del value, x, w
            save()
        report['results'][-1]['maxSampleDifference'] = float((outputs[0] - outputs[1]).abs().max())
        del source, weight, outputs
    report['status'] = 'COMPLETE_DIAGNOSTIC'
finally:
    report['finishedAt'] = datetime.now(timezone.utc).isoformat()
    save()
    print(json.dumps(report))
