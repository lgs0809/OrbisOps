"""Native installed Torch/Transformers tests, without model weights or downloads."""
import unittest
from types import SimpleNamespace
import torch
from transformers.integrations.sdpa_attention import sdpa_attention_forward, repeat_kv, use_gqa_in_sdpa
from qwen_cpu_attention import build_attention


class AttentionTest(unittest.TestCase):
    def setUp(self):
        torch.set_num_threads(2)
        torch.manual_seed(37)
        self.module = SimpleNamespace(num_key_value_groups=2, is_causal=True, training=False)
        self.expanded = build_attention(sdpa_attention_forward, repeat_kv, use_gqa_in_sdpa, 'expanded-kv')

    def compare(self, dtype, mask=None, **kwargs):
        query = torch.randn(1, 4, 17, 16, dtype=dtype)
        key = torch.randn(1, 2, 17, 16, dtype=dtype)
        value = torch.randn(1, 2, 17, 16, dtype=dtype)
        expected, weights = sdpa_attention_forward(self.module, query, key, value, mask, **kwargs)
        actual, actual_weights = self.expanded(self.module, query, key, value, mask, **kwargs)
        tolerance = 0.02 if dtype == torch.bfloat16 else 0.00001
        torch.testing.assert_close(expected, actual, rtol=tolerance, atol=tolerance)
        self.assertIsNone(weights)
        self.assertIsNone(actual_weights)
        self.assertEqual(2, self.module.num_key_value_groups)
        return query, key, value, actual

    def test_float32_causal_gqa_keeps_complete_sequence(self):
        self.compare(torch.float32)

    def test_bfloat16_causal_gqa_preserves_dtype(self):
        self.assertEqual(torch.bfloat16, self.compare(torch.bfloat16)[3].dtype)

    def test_padding_mask_and_scale_are_preserved(self):
        mask = torch.ones(1, 1, 17, 17, dtype=torch.bool).tril()
        mask[:, :, :, 3] = False
        query, key, value, actual = self.compare(torch.float32, mask=mask, scaling=0.12)
        changed = value.clone()
        changed[:, :, 3, :] = 10000
        changed_output, _ = self.expanded(self.module, query, key, changed, mask, scaling=0.12)
        torch.testing.assert_close(actual, changed_output)

    def test_position_bias_and_non_causal_override_are_preserved(self):
        bias = torch.randn(1, 4, 17, 17) * 0.1
        self.compare(torch.float32, position_bias=bias, is_causal=False)

    def test_framework_positional_arguments_are_preserved(self):
        query = torch.randn(1, 4, 17, 16)
        key = torch.randn(1, 2, 17, 16)
        value = torch.randn(1, 2, 17, 16)
        expected, _ = sdpa_attention_forward(self.module, query, key, value, None, 0.0, 0.12, False)
        actual, _ = self.expanded(self.module, query, key, value, None, 0.0, 0.12, False)
        torch.testing.assert_close(expected, actual, rtol=0.00001, atol=0.00001)

    def test_native_policy_delegates_without_expanding(self):
        seen = []
        original = lambda *a, **kw: seen.append((a, kw))
        native = build_attention(original, lambda *a: self.fail('Native policy must not expand'),
                                 lambda *a: True, 'native')
        query = torch.empty(1, 4, 3, 16)
        key = torch.empty(1, 2, 3, 16)
        native(self.module, query, key, key, None, is_causal=False)
        self.assertIs(self.module, seen[0][0][0])
        self.assertIs(key, seen[0][0][2])
        self.assertFalse(seen[0][1]['is_causal'])

    def test_registry_preserves_existing_mask_formatter(self):
        from transformers import AttentionInterface, AttentionMaskInterface
        from qwen_cpu_attention import configure
        masks_before = dict(AttentionMaskInterface._global_mapping)
        original = AttentionInterface._global_mapping['sdpa']
        try:
            self.assertFalse(configure()['maskFormatterChanged'])
            self.assertEqual(masks_before, AttentionMaskInterface._global_mapping)
        finally:
            AttentionInterface.register('sdpa', original)

    def test_invalid_policy_is_rejected(self):
        with self.assertRaises(ValueError):
            build_attention(sdpa_attention_forward, repeat_kv, use_gqa_in_sdpa, 'invalid')

    def test_fp32_cpu_attention_retains_original_output_and_module(self):
        wrapped = build_attention(sdpa_attention_forward, repeat_kv, use_gqa_in_sdpa, 'expanded-kv-fp32')
        for dtype in (torch.bfloat16, torch.float16):
            query = torch.randn(1, 4, 17, 16, dtype=dtype)
            key = torch.randn(1, 2, 17, 16, dtype=dtype)
            value = torch.randn_like(key)
            expected, _ = sdpa_attention_forward(self.module, query.float(), key.float(), value.float(), None)
            actual, weights = wrapped(self.module, query, key, value, None)
            torch.testing.assert_close(expected.to(dtype), actual, rtol=0.008, atol=0.008)
            self.assertEqual(dtype, actual.dtype)
            self.assertIsNone(weights)
            self.assertEqual(2, self.module.num_key_value_groups)

    def test_fp32_policy_preserves_boolean_padding_and_causality(self):
        wrapped = build_attention(sdpa_attention_forward, repeat_kv, use_gqa_in_sdpa, 'expanded-kv-fp32')
        query = torch.randn(1, 4, 17, 16, dtype=torch.bfloat16)
        key = torch.randn(1, 2, 17, 16, dtype=torch.bfloat16)
        value = torch.randn_like(key)
        mask = torch.ones(1, 1, 17, 17, dtype=torch.bool).tril()
        mask[:, :, :, 3] = False
        actual, _ = wrapped(self.module, query, key, value, mask, scaling=0.12)
        changed = value.clone(); changed[:, :, 3, :] = 10000
        other, _ = wrapped(self.module, query, key, changed, mask, scaling=0.12)
        torch.testing.assert_close(actual, other)

    def test_fp32_policy_preserves_float_mask_and_positional_bias(self):
        wrapped = build_attention(sdpa_attention_forward, repeat_kv, use_gqa_in_sdpa, 'expanded-kv-fp32')
        query = torch.randn(1, 4, 17, 16, dtype=torch.bfloat16)
        key = torch.randn(1, 2, 17, 16, dtype=torch.bfloat16)
        value = torch.randn_like(key)
        mask = torch.zeros(1, 1, 17, 17, dtype=torch.bfloat16)
        mask[:, :, :, 3] = float('-inf')
        bias = torch.randn(1, 4, 17, 17, dtype=torch.bfloat16) * 0.1
        expected, _ = sdpa_attention_forward(self.module, query.float(), key.float(), value.float(),
                                            mask.float(), 0.0, 0.12, False)
        actual, _ = wrapped(self.module, query, key, value, mask, 0.0, 0.12, False)
        torch.testing.assert_close(expected.to(query.dtype), actual, rtol=0.008, atol=0.008)
        # Installed Transformers requires a boolean mask when position_bias is present.
        boolean_mask = mask == 0
        expected, _ = sdpa_attention_forward(self.module, query.float(), key.float(), value.float(),
                                            boolean_mask, 0.0, 0.12, False, bias.float())
        actual, _ = wrapped(self.module, query, key, value, boolean_mask, 0.0, 0.12, False, bias)
        torch.testing.assert_close(expected.to(query.dtype), actual, rtol=0.008, atol=0.008)

    def test_fp32_policy_does_not_change_full_precision_inputs(self):
        seen = []
        original = lambda *a, **kw: seen.append((a, kw))
        wrapped = build_attention(original, repeat_kv, use_gqa_in_sdpa, 'expanded-kv-fp32')
        module = SimpleNamespace(num_key_value_groups=1)
        query = torch.empty(1, 2, 3, 16, dtype=torch.float64)
        wrapped(module, query, query, query, None, is_causal=False)
        self.assertIs(query, seen[0][0][1])
        self.assertIs(module, seen[0][0][0])


if __name__ == '__main__':
    unittest.main()
