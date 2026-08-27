"""Resource lifecycle tests; a sleeping fault kernel is not model-quality evidence."""
import os
from pathlib import Path
import subprocess
import sys
import threading
import time
import unittest
from fastapi import HTTPException
from qwen_inference_admission import GATE, InferenceAdmission, infer, REQUEST_DEADLINE, NativeRequestBudget


class AdmissionTest(unittest.TestCase):
    def test_concurrent_kernel_is_rejected_and_success_or_error_releases_resources(self):
        entered, finish = threading.Event(), threading.Event()
        values = []

        def kernel():
            entered.set()
            self.assertTrue(finish.wait(2))
            return 42

        thread = threading.Thread(target=lambda: values.append(infer(kernel)))
        thread.start()
        self.assertTrue(entered.wait(1))
        self.assertTrue(GATE.snapshot()['busy'])
        self.assertEqual('kernel', GATE.snapshot()['purpose'])
        try:
            with self.assertRaises(HTTPException) as error:
                infer(lambda: self.fail('A second kernel must not launch'), _admission_wait_seconds=0)
            self.assertEqual(503, error.exception.status_code)
        finally:
            finish.set()
            thread.join(3)
        self.assertEqual([42], values)
        self.assertFalse(GATE.snapshot()['busy'])

        def broken():
            raise ValueError('Injected inference failure')

        with self.assertRaises(ValueError):
            infer(broken)
        self.assertFalse(GATE.snapshot()['busy'])
        self.assertEqual(7, infer(lambda: 7))

    def test_hard_deadline_disposes_the_actual_child_process(self):
        environment = dict(os.environ, INFERENCE_RESOURCE_DEADLINE_SECONDS='0.3',
                           PYTHONPATH=str(Path(__file__).resolve().parent))
        code = "from qwen_inference_admission import infer; import time; infer(time.sleep, 20); print('UNREACHABLE_SUCCESS')"
        started = time.monotonic()
        result = subprocess.run([sys.executable, '-c', code], env=environment,
                                capture_output=True, text=True, timeout=5)
        self.assertEqual(75, result.returncode)
        self.assertIn('INFERENCE_RESOURCE_DEADLINE_EXCEEDED', result.stderr)
        self.assertNotIn('UNREACHABLE_SUCCESS', result.stdout)
        self.assertLess(time.monotonic() - started, 5)

    def test_invalid_deadlines_are_configuration_errors(self):
        for value in (0, -1, float('nan'), float('inf'), 3601):
            with self.assertRaises(ValueError):
                InferenceAdmission(value)

    def test_fifo_waiters_cannot_be_overtaken_by_fail_fast_callers(self):
        gate = InferenceAdmission(max_waiters=3)
        self.assertTrue(gate.acquire(blocking=False))
        order = []
        def worker(number):
            self.assertTrue(gate.acquire(timeout=2, purpose=str(number)))
            order.append(number)
            time.sleep(.01)
            gate.release()
        threads = []
        for number in range(3):
            thread = threading.Thread(target=worker, args=(number,)); thread.start(); threads.append(thread)
            end = time.monotonic() + 1
            while gate.snapshot()['waiting'] != number + 1 and time.monotonic() < end:
                time.sleep(.001)
            self.assertEqual(number + 1, gate.snapshot()['waiting'])
        self.assertFalse(gate.acquire(timeout=.01))
        self.assertFalse(gate.acquire(blocking=False))
        gate.release()
        for thread in threads: thread.join(3)
        self.assertEqual([0, 1, 2], order)
        self.assertFalse(gate.snapshot()['busy'])
        self.assertEqual(0, gate.snapshot()['waiting'])

    def test_expired_waiter_is_removed_without_releasing_running_kernel(self):
        gate = InferenceAdmission()
        self.assertTrue(gate.acquire(blocking=False, purpose='running'))
        self.assertFalse(gate.acquire(timeout=.02))
        self.assertEqual(0, gate.snapshot()['waiting'])
        self.assertTrue(gate.snapshot()['busy'])
        self.assertEqual('running', gate.snapshot()['purpose'])
        gate.release()
        self.assertTrue(gate.acquire(blocking=False)); gate.release()

    def test_expired_request_never_starts_another_kernel(self):
        token = REQUEST_DEADLINE.set(time.monotonic() - 1)
        try:
            with self.assertRaises(HTTPException) as error:
                infer(lambda: self.fail('Expired work must not launch'))
            self.assertEqual('RETRIEVAL_REQUEST_DEADLINE_EXCEEDED', error.exception.detail)
            self.assertFalse(GATE.snapshot()['busy'])
        finally:
            REQUEST_DEADLINE.reset(token)

    def test_native_request_budget_is_propagated_and_reset(self):
        import asyncio
        seen = []
        async def downstream(scope, receive, send):
            seen.append(REQUEST_DEADLINE.get() - time.monotonic())
        middleware = NativeRequestBudget(downstream)
        asyncio.run(middleware({'path': '/v1/rerank'}, None, None))
        self.assertTrue(59 <= seen[0] <= 60)
        self.assertIsNone(REQUEST_DEADLINE.get())


if __name__ == '__main__':
    unittest.main()
