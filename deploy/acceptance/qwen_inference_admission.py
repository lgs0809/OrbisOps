"""Shared inference admission with a hard resource deadline supervised by Docker.

HTTP cancellation cannot stop a running CPU model kernel. Do not release its permit
and launch another kernel; recycle this stateless process after its resource deadline.
The existing container restart policy reloads the same cached weights.
"""
import logging
import os
import threading
import time
from collections import deque
from contextvars import ContextVar
from fastapi import HTTPException

REQUEST_DEADLINE = ContextVar('retrieval_request_deadline', default=None)


def remaining_request_seconds(default):
    deadline = REQUEST_DEADLINE.get()
    return min(default, max(0, deadline - time.monotonic())) if deadline is not None else default


def check_request_budget():
    if remaining_request_seconds(1) <= 0:
        raise HTTPException(503, 'RETRIEVAL_REQUEST_DEADLINE_EXCEEDED')


class NativeRequestBudget:
    """The native request budget includes queueing, loading and scoring.

    Context variables propagate through Starlette's existing thread-pool boundary.
    The OrbisOps five-second route retains its separate fail-fast contract.
    """
    def __init__(self, app):
        self.app = app

    async def __call__(self, scope, receive, send):
        path = scope.get('path', '')
        seconds = (60 if path in ('/v1/rerank', '/orbisops/embed') else 120
                   if path in ('/v1/embeddings', '/v1/multimodalembeddings', '/v1/embedding-token-count') else None)
        token = REQUEST_DEADLINE.set(time.monotonic() + seconds) if seconds else None
        try:
            await self.app(scope, receive, send)
        finally:
            if token is not None:
                REQUEST_DEADLINE.reset(token)


class InferenceAdmission:
    def __init__(self, max_seconds=180, max_waiters=8):
        if not 0 < max_seconds <= 3600:
            raise ValueError('Inference resource deadline must be positive and at most one hour')
        self.max_seconds = max_seconds
        if not isinstance(max_waiters, int) or max_waiters < 1:
            raise ValueError('Waiting capacity must be a positive integer')
        self.max_waiters = max_waiters
        self._lock = threading.Lock()
        self._condition = threading.Condition(self._lock)
        self._waiting = deque()
        self._active = None
        threading.Thread(target=self._watch, name='inference-resource-watchdog', daemon=True).start()

    def acquire(self, blocking=True, timeout=None, *, purpose='inference'):
        deadline = time.monotonic() + timeout if timeout is not None else None
        with self._condition:
            if self._active is None and not self._waiting:
                self._active = (time.monotonic(), purpose)
                return True
            if not blocking or len(self._waiting) >= self.max_waiters:
                return False
            ticket = object()
            self._waiting.append(ticket)
            try:
                while self._active is not None or self._waiting[0] is not ticket:
                    remaining = deadline - time.monotonic() if deadline is not None else None
                    if remaining is not None and remaining <= 0:
                        return False
                    self._condition.wait(remaining)
                if deadline is not None and time.monotonic() >= deadline:
                    return False
                self._active = (time.monotonic(), purpose)
                return True
            finally:
                self._waiting.remove(ticket)
                self._condition.notify_all()

    def release(self):
        with self._condition:
            if self._active is None:
                raise RuntimeError('No inference owns the resource permit')
            self._active = None
            self._condition.notify_all()

    def snapshot(self):
        with self._lock:
            active = self._active
            return {'busy': active is not None,
                    'purpose': active[1] if active else None,
                    'elapsedSeconds': round(time.monotonic() - active[0], 3) if active else 0,
                    'resourceDeadlineSeconds': self.max_seconds,
                    'waiting': len(self._waiting), 'waitingCapacity': self.max_waiters,
                    'admissionPolicy': 'bounded-fifo-v1'}

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


def infer(operation, *args, _admission_purpose=None, _admission_wait_seconds=None, **kwargs):
    check_request_budget()
    # Keep a native request's FIFO position for its remaining observation budget.
    # A shorter fixed wait made it leave the queue and lose to later fail-fast
    # callers during client backoff, even though it still had time to execute.
    wait = remaining_request_seconds(120 if REQUEST_DEADLINE.get() is not None else 55)
    if _admission_wait_seconds is not None:
        wait = min(wait, _admission_wait_seconds)
    if not GATE.acquire(blocking=wait > 0, timeout=wait if wait > 0 else None,
                        purpose=_admission_purpose or getattr(operation, '__name__', 'inference')):
        raise HTTPException(503, 'RETRIEVAL_BUSY')
    try:
        check_request_budget()
        return operation(*args, **kwargs)
    finally:
        GATE.release()
