"""Lifecycle boundaries only. Lightweight objects are not actual Qwen evidence."""
import threading
import unittest
import weakref
from concurrent.futures import ThreadPoolExecutor
from qwen_model_residency import ModelResidency


class Model:
    pass


class ResidencyTest(unittest.TestCase):
    def test_concurrent_first_calls_construct_only_once(self):
        entered, finish = threading.Event(), threading.Event()
        calls = []
        def factory():
            calls.append(1)
            entered.set()
            self.assertTrue(finish.wait(2))
            return Model()
        manager = ModelResidency({'embedding': factory}, lambda *a, **k: None)
        with ThreadPoolExecutor(max_workers=2) as pool:
            first = pool.submit(manager.load, 'embedding')
            self.assertTrue(entered.wait(1))
            second = pool.submit(manager.load, 'embedding')
            snapshot = manager.snapshot()
            self.assertEqual('embedding', snapshot['loadingKind'])
            self.assertIsNone(snapshot['residentKind'])
            finish.set()
            self.assertIs(first.result(2), second.result(2))
        self.assertEqual([1], calls)
        self.assertEqual({'embedding': 1}, manager.snapshot()['loadCounts'])

    def test_previous_model_is_disposed_before_other_constructor_runs(self):
        old = []
        states = []
        def embedding():
            model = Model()
            old.append(weakref.ref(model))
            return model
        def rerank():
            self.assertIsNone(old[-1]())
            return Model()
        manager = ModelResidency({'embedding': embedding, 'rerank': rerank},
                                  lambda *a, **k: states.append((a, k)))
        manager.load('embedding')
        manager.load('rerank')
        self.assertEqual('rerank', manager.snapshot()['residentKind'])
        self.assertEqual(['embedding', 'rerank'], manager.snapshot()['verifiedKinds'])
        self.assertIn((('embedding', 'evicted'), {'resident': False}), states)
        manager.load('embedding')
        self.assertEqual({'embedding': 2, 'rerank': 1}, manager.snapshot()['loadCounts'])

    def test_constructor_failure_has_no_retained_model_and_can_retry(self):
        calls = []
        def factory():
            calls.append(1)
            if len(calls) == 1:
                raise RuntimeError('Injected failure')
            return Model()
        manager = ModelResidency({'embedding': factory}, lambda *a, **k: None)
        with self.assertRaises(RuntimeError):
            manager.load('embedding')
        self.assertEqual([], manager.snapshot()['verifiedKinds'])
        self.assertIsNone(manager.snapshot()['loadingKind'])
        manager.load('embedding')
        self.assertEqual(['embedding'], manager.snapshot()['verifiedKinds'])


if __name__ == '__main__':
    unittest.main()
