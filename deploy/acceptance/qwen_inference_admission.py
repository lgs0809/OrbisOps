"""Shared inference admission with a hard resource deadline supervised by Docker.

HTTP cancellation cannot stop a running CPU model kernel. Do not release its permit
and launch another kernel; recycle this stateless process after its resource deadline.
The existing container restart policy reloads the same cached weights.
"""
import logging
import os
import threading
import time
from fastapi import HTTPException


class InferenceAdmission:
    def __init__(self, max_seconds=180):
        if not 0 < max_seconds <= 3600:
            raise ValueError('Inference resource deadline must be positive and at most one hour')
        self.max_seconds = max_seconds
        self._permit = threading.BoundedSemaphore(1)
        self._lock = threading.Lock()
        self._active = None
        threading.Thread(target=self._watch, name='inference-resource-watchdog', daemon=True).start()

    def acquire(self, blocking=True, timeout=None, *, purpose='inference'):
        acquired = self._permit.acquire(blocking=blocking, timeout=timeout)
        if acquired:
            with self._lock:
                self._active = (time.monotonic(), purpose)
        return acquired

    def release(self):
        with self._lock:
            self._active = None
            self._permit.release()

    def snapshot(self):
        with self._lock:
            active = self._active
            return {'busy': active is not None,
                    'purpose': active[1] if active else None,
                    'elapsedSeconds': round(time.monotonic() - active[0], 3) if active else 0,
                    'resourceDeadlineSeconds': self.max_seconds}

    def _watch(self):
        while True:
            time.sleep(min(1, self.max_seconds / 4))
            with self._lock:
                if self._active and time.monotonic() - self._active[0] >= self.max_seconds:
                    logging.error('INFERENCE_RESOURCE_DEADLINE_EXCEEDED purpose=%s deadlineSeconds=%s; '
                                  'recycling stateless model process', self._active[1], self.max_seconds)
                    # Exiting owns this process only. Never unlock an in-flight kernel's permit.
                    os._exit(75)


GATE = InferenceAdmission(float(os.getenv('INFERENCE_RESOURCE_DEADLINE_SECONDS', '180')))


def infer(operation, *args, _admission_purpose=None, **kwargs):
    if not GATE.acquire(blocking=False, purpose=_admission_purpose or getattr(operation, '__name__', 'inference')):
        raise HTTPException(503, 'RETRIEVAL_BUSY')
    try:
        return operation(*args, **kwargs)
    finally:
        GATE.release()
