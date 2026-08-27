"""One resident model, using the existing constructors and unchanged cached weights.

Every caller must hold inference admission throughout loading and use. Loading is
single flight; eviction drops the previous model before constructing the next.
Readiness distinguishes successfully verified weights from current residency.
"""
import gc
import threading


class ModelResidency:
    def __init__(self, factories, mark_state, collect=gc.collect):
        self.factories = factories
        self.mark_state = mark_state
        self.collect = collect
        self._load_lock = threading.Lock()
        self._state_lock = threading.Lock()
        self._resident = None
        self._kind = None
        self._loading = None
        self._verified = set()
        self._loads = {kind: 0 for kind in factories}

    def load(self, kind):
        if kind not in self.factories:
            raise ValueError('Unknown model kind')
        with self._load_lock:
            if self._kind == kind and self._resident is not None:
                return self._resident
            with self._state_lock:
                previous = self._kind
                self._resident = None
                self._kind = None
                self._loading = kind
            if previous is not None:
                self.mark_state(previous, 'evicted', resident=False)
                self.collect()
            try:
                model = self.factories[kind]()
            except BaseException:
                with self._state_lock:
                    self._loading = None
                    self._verified.discard(kind)
                raise
            with self._state_lock:
                self._resident = model
                self._kind = kind
                self._loading = None
                self._verified.add(kind)
                self._loads[kind] += 1
            underlying = (getattr(model._first_module(), 'auto_model', None)
                          if hasattr(model, '_first_module') else getattr(model, 'model', None))
            config = getattr(underlying, 'config', None)
            self.mark_state(kind, 'loaded', resident=True,
                            parameterDtype=str(getattr(underlying, 'dtype', 'unavailable')),
                            attentionImplementation=getattr(config, '_attn_implementation', None),
                            runtimeClass=type(underlying).__name__ if underlying is not None else None,
                            loadedRevision=getattr(config, '_commit_hash', None))
            return model

    def snapshot(self):
        # Never wait for model loading on the HTTP event loop.
        with self._state_lock:
            return {'capacity': 1, 'residentKind': self._kind,
                    'loadingKind': self._loading,
                    'verifiedKinds': sorted(self._verified),
                    'loadCounts': dict(self._loads)}


def install(source):
    # functools exposes __wrapped__ for replacing its cache policy. The old
    # caches have not run at import time and must never retain a second model.
    factories = {'embedding': source.embedding_model.__wrapped__,
                 'rerank': source.rerank_model.__wrapped__}
    manager = ModelResidency(factories, source.mark_model_state)
    source.embedding_model = lambda: manager.load('embedding')
    source.rerank_model = lambda: manager.load('rerank')
    return manager
