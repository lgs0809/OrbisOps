#!/usr/bin/env python3
"""Profile a synthetic full-shape CPU SDPA operator; never load model weights.

Run in a bounded, network-disabled diagnostic container using the original
embedding image. This is an operator observation, not a complete Qwen replay,
model quality score, or change to the serving runtime.
"""
import argparse
from datetime import datetime, timezone
import gc
import json
from pathlib import Path
import time
from types import SimpleNamespace

import torch
from transformers.integrations.sdpa_attention import sdpa_attention_forward, repeat_kv


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--tokens', type=int, required=True)
    parser.add_argument('--mode', choices=('native', 'expanded-kv'), required=True)
    parser.add_argument('--compute-dtype', choices=('bfloat16', 'float32'), default='bfloat16')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if not 1 <= args.tokens <= 2754 or args.output.exists():
        raise ValueError('Use a fresh report and the observed full-shape token bound')
    torch.set_num_threads(2)
    torch.manual_seed(79)
    report = dict(status='RUNNING_OPERATOR_DIAGNOSTIC', startedAt=datetime.now(timezone.utc).isoformat(),
                  torchVersion=torch.__version__, threads=torch.get_num_threads(), mode=args.mode,
                  tokens=args.tokens, sourceDtype='torch.bfloat16',
                  computeDtype='torch.' + args.compute_dtype, device='cpu', attentionMask=None,
                  isCausal=True, modelCalls=0, weightsLoaded=False,
                  boundary='Synthetic Q/K/V with observed head/sequence shapes; tensor strides may differ from the '
                  'loaded model. Native installed Transformers SDPA and Torch CPU profiler only. Does not prove '
                  'whole-model timing, semantic quality or the actual serving operator dispatch.')

    def save():
        args.output.write_text(json.dumps(report, indent=2) + '\n')

    save()
    try:
        query = torch.randn(1, 16, args.tokens, 128, dtype=torch.bfloat16)
        key = torch.randn(1, 8, args.tokens, 128, dtype=torch.bfloat16)
        value = torch.randn_like(key)
        report['inputShapes'] = [list(t.shape) for t in (query, key, value)]
        report['inputStrides'] = [list(t.stride()) for t in (query, key, value)]
        if args.compute_dtype == 'float32':
            # Exact upcast of the same BF16 synthetic tensors, not different random inputs.
            query, key, value = query.float(), key.float(), value.float()
        module = SimpleNamespace(num_key_value_groups=2, is_causal=True)
        start = time.monotonic()
        with torch.inference_mode(), torch.profiler.profile(
                activities=[torch.profiler.ProfilerActivity.CPU], record_shapes=False,
                profile_memory=False) as profile:
            if args.mode == 'expanded-kv':
                key, value = repeat_kv(key, 2), repeat_kv(value, 2)
                module = SimpleNamespace(num_key_value_groups=1, is_causal=True)
            output, _ = sdpa_attention_forward(module, query, key, value, None)
        report['operatorSecondsWithProfiler'] = time.monotonic() - start
        report['operators'] = [dict(name=e.key, count=e.count, selfCpuMicroseconds=e.self_cpu_time_total)
                               for e in profile.key_averages()
                               if 'attention' in e.key or e.key in ('aten::bmm', 'aten::_softmax')]
        report['finite'] = bool(torch.isfinite(output).all())
        report['outputShape'] = list(output.shape)
        report['outputSample'] = output[0, :2, :2, :4].float().tolist()
        report['status'] = 'COMPLETE_OPERATOR_DIAGNOSTIC' if report['finite'] else 'FAIL_NONFINITE_OPERATOR'
        del query, key, value, output
        gc.collect()
    except Exception as failure:
        report.update(status='FAIL_OPERATOR_DIAGNOSTIC', failureType=type(failure).__name__, failure=str(failure))
    finally:
        report['finishedAt'] = datetime.now(timezone.utc).isoformat()
        save()
        print(json.dumps(report))
    raise SystemExit(int(not report['status'].startswith('COMPLETE')))


if __name__ == '__main__':
    main()
