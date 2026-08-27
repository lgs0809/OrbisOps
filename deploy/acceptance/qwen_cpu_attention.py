"""CPU GQA compatibility through the installed Transformers attention registry.

Keep the existing SDPA mask formatter and delegate every attention operation to
Transformers. The opt-in CPU policy expands grouped keys/values using its own
repeat_kv helper, enabling PyTorch's supported CPU SDPA kernels. A separate
opt-in CPU policy upcasts attention inputs to FP32 and restores the original
output dtype. Model weights, prompts, masks, causal rules and non-CPU dispatch
remain unchanged; FP32 attention can produce different rounding and requires
native numerical and complete-model verification before activation.
"""
import json
import logging
import os

LOGGER = logging.getLogger('uvicorn.error')


class ExpandedGroups:
    num_key_value_groups = 1

    def __init__(self, module):
        self.module = module

    def __getattr__(self, name):
        return getattr(self.module, name)


def build_attention(original, repeat_kv, use_gqa, policy, trace_provider=lambda: None):
    if policy not in ('native', 'expanded-kv', 'expanded-kv-fp32'):
        raise ValueError('Unknown CPU attention policy')
    last_trace = [None]

    def attention(module, query, key, value, attention_mask, *args, **kwargs):
        groups = getattr(module, 'num_key_value_groups', 1)
        native_gqa = groups > 1 and use_gqa(attention_mask, key, value)
        expanded = policy in ('expanded-kv', 'expanded-kv-fp32') and query.device.type == 'cpu' and native_gqa
        source_dtype = query.dtype
        upcast = False
        if policy == 'expanded-kv-fp32' and query.device.type == 'cpu':
            import torch
            upcast = source_dtype in (torch.bfloat16, torch.float16)
        trace = trace_provider()
        if trace is not None and trace != last_trace[0]:
            last_trace[0] = trace
            LOGGER.info('RETRIEVAL_ATTENTION %s', json.dumps({
                'requestPurpose': trace, 'policy': policy, 'device': str(query.device),
                'dtype': str(query.dtype), 'queryShape': list(query.shape),
                'keyShape': list(key.shape), 'valueShape': list(value.shape),
                'maskShape': list(attention_mask.shape) if attention_mask is not None else None,
                'moduleClass': type(module).__name__, 'keyValueGroups': groups,
                'nativeWouldEnableGqa': native_gqa, 'expandedCpuGroups': expanded,
                'attentionComputeDtype': 'torch.float32' if upcast else str(source_dtype),
                'attentionOutputDtype': str(source_dtype), 'upcastAttentionInputs': upcast,
            }))
        if expanded:
            key = repeat_kv(key, groups)
            value = repeat_kv(value, groups)
            module = ExpandedGroups(module)
        if upcast:
            query, key, value = query.float(), key.float(), value.float()
            if attention_mask is not None and attention_mask.is_floating_point():
                attention_mask = attention_mask.float()
            # position_bias is the fourth optional positional argument in installed
            # Transformers SDPA. Preserve every argument and only upcast this tensor.
            if len(args) > 3 and args[3] is not None and args[3].is_floating_point():
                args = (*args[:3], args[3].float(), *args[4:])
            if kwargs.get('position_bias') is not None and kwargs['position_bias'].is_floating_point():
                kwargs = dict(kwargs, position_bias=kwargs['position_bias'].float())
        result = original(module, query, key, value, attention_mask, *args, **kwargs)
        if upcast:
            return result[0].to(source_dtype), result[1]
        return result

    return attention


def configure():
    from transformers import AttentionInterface
    from transformers.integrations.sdpa_attention import sdpa_attention_forward, repeat_kv, use_gqa_in_sdpa
    from qwen_inference_admission import GATE
    policy = os.getenv('INFERENCE_CPU_ATTENTION', 'native')
    callback = build_attention(sdpa_attention_forward, repeat_kv, use_gqa_in_sdpa,
                               policy, lambda: GATE.snapshot()['purpose'])
    # Reuse the existing "sdpa" key: its framework mask registration is retained.
    AttentionInterface.register('sdpa', callback)
    return {'policy': policy, 'registration': 'Transformers.AttentionInterface',
            'attentionImplementation': 'sdpa', 'maskFormatterChanged': False,
            'weightsChanged': False, 'dtypeChanged': False,
            'attentionComputeUpcastOnCpu': policy == 'expanded-kv-fp32',
            'modelParameterAndOutputDtypeChanged': False}
