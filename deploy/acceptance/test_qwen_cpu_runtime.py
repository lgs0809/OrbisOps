import unittest
from qwen_cpu_runtime import configure


class TorchProbe:
    def __init__(self):
        self.calls = []
        self.intra = self.inter = 0

    def set_num_threads(self, value):
        self.calls.append(('intra', value))
        self.intra = value

    def set_num_interop_threads(self, value):
        self.calls.append(('inter', value))
        self.inter = value

    def get_num_threads(self):
        return self.intra

    def get_num_interop_threads(self):
        return self.inter


class CpuRuntimeTest(unittest.TestCase):
    def test_explicit_budget_initializes_native_pools_and_reports_actual_settings(self):
        env = {'INFERENCE_CPU_THREADS': '3', 'INFERENCE_INTEROP_THREADS': '1'}
        torch = TorchProbe()
        actual = configure(env, torch)
        self.assertEqual([('intra', 3), ('inter', 1)], torch.calls)
        self.assertEqual(3, actual['intraOpThreads'])
        self.assertEqual(1, actual['interOpThreads'])
        self.assertEqual('3', env['OMP_NUM_THREADS'])
        self.assertEqual('3', env['MKL_NUM_THREADS'])
        self.assertEqual('false', env['TOKENIZERS_PARALLELISM'])

    def test_existing_explicit_library_preferences_remain_preserved(self):
        env = {'OMP_NUM_THREADS': '1', 'TOKENIZERS_PARALLELISM': 'true'}
        configure(env, TorchProbe())
        self.assertEqual('1', env['OMP_NUM_THREADS'])
        self.assertEqual('true', env['TOKENIZERS_PARALLELISM'])

    def test_invalid_config_fails_before_any_model_or_kernel_starts(self):
        for name, value in [('INFERENCE_CPU_THREADS', '0'), ('INFERENCE_INTEROP_THREADS', '257'),
                            ('INFERENCE_CPU_THREADS', 'not-a-number')]:
            torch = TorchProbe()
            with self.assertRaises(ValueError):
                configure({name: value}, torch)
            self.assertEqual([], torch.calls)


if __name__ == '__main__':
    unittest.main()
