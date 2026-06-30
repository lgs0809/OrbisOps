"""Resource lifecycle tests; a sleeping fault kernel is not model-quality evidence."""
import os
from pathlib import Path
import subprocess
import sys
import threading
import time
import unittest
from fastapi import HTTPException
from qwen_inference_admission import GATE, InferenceAdmission, infer


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
                infer(lambda: self.fail('A second kernel must not launch'))
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


if __name__ == '__main__':
    unittest.main()
