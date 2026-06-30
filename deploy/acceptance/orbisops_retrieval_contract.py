"""Strict OrbisOps text routes sharing the existing embedding-model singletons."""
from collections import OrderedDict
import hashlib
import json
import time
import math
import os
import threading
import unicodedata
from fastapi import HTTPException, Request
from starlette.concurrency import run_in_threadpool
from qwen_inference_admission import GATE
from qwen_embedding_length_guard import inspect_encoder_inputs

EMBEDDING = 'Qwen/Qwen3-VL-Embedding-2B'
RERANKER = 'Qwen/Qwen3-VL-Reranker-2B'
EMBEDDING_REVISION = '9f2f7e710d6d81056aa5c0a4f04764fec6bb7bda'
RERANKER_REVISION = '4bd860ac4f15ad1897a214615cccc700f8f71818'
PREPROCESS = 'skill-route-text-v1:nfc:instruction:mrl1024:l2'
INSTRUCTION = 'Retrieve relevant operational procedures for the query.'
RERANK_BUDGET_SECONDS = 5


def bounded_rerank(model, pairs, activation, clock=time.monotonic):
    """Stop between CPU inferences after the caller's budget; never publish partial scores.

    A running model kernel cannot be interrupted by an HTTP disconnect. Single-pair batches
    avoid dispatching all twenty expensive pairs after the caller has already fallen back.
    One in-flight pair can still overrun the deadline, but no remaining pairs are launched.
    """
    deadline = clock() + RERANK_BUDGET_SECONDS
    values = []
    for pair in pairs:
        if clock() >= deadline:
            raise HTTPException(503, 'RERANK_BUDGET_EXCEEDED')
        score = model.predict([pair], prompt=INSTRUCTION, batch_size=1,
                              activation_fn=activation).tolist()
        if len(score) != 1 or not math.isfinite(float(score[0])):
            raise HTTPException(503, 'RERANK_RESULT_INVALID')
        values.append(float(score[0]))
    if clock() >= deadline:
        raise HTTPException(503, 'RERANK_BUDGET_EXCEEDED')
    return values


def normalize_text(value):
    if not isinstance(value, str) or not value.strip():
        raise HTTPException(400, 'TEXT_REQUIRED')
    return unicodedata.normalize('NFC', value)


def validate(data, kind):
    model, revision = ((EMBEDDING, EMBEDDING_REVISION) if kind == 'embedding'
                       else (RERANKER, RERANKER_REVISION))
    if not isinstance(data, dict) or any(data.get(k) != v for k, v in (
            ('model', model), ('revision', revision), ('preprocessing', PREPROCESS))):
        raise HTTPException(400, 'MODEL_CONTRACT_MISMATCH')
    if kind == 'embedding':
        if data.get('dimensions') != 1024 or data.get('kind') not in ('query', 'document'):
            raise HTTPException(400, 'EMBEDDING_CONTRACT_INVALID')
        normalize_text(data.get('text'))
    else:
        normalize_text(data.get('query'))
        docs = data.get('documents')
        if not isinstance(docs, list) or not 1 <= len(docs) <= 20:
            raise HTTPException(400, 'RERANK_CANDIDATES_INVALID')
        ids = []
        for document in docs:
            if not isinstance(document, dict):
                raise HTTPException(400, 'RERANK_CANDIDATES_INVALID')
            ids.append(normalize_text(document.get('id')))
            normalize_text(document.get('text'))
        if len(set(ids)) != len(ids):
            raise HTTPException(400, 'RERANK_CANDIDATES_INVALID')
    return model, revision


def assert_loaded_revision(model, revision, kind):
    underlying = model[0].auto_model if kind == 'embedding' else model.model
    if getattr(underlying.config, '_commit_hash', None) != revision:
        raise HTTPException(503, 'LOADED_MODEL_REVISION_MISMATCH')


class DocumentCache:
    """Bounded, process-local results only; exact contract/content key, no disk writes."""
    def __init__(self, capacity=128, ttl=900, clock=time.monotonic):
        self.capacity, self.ttl, self.clock = capacity, ttl, clock
        self.items, self.lock = OrderedDict(), threading.Lock()

    def key(self, data, kind):
        if kind != 'embedding' or data['kind'] != 'document':
            return None
        content = dict(data, text=normalize_text(data['text']))
        return hashlib.sha256(json.dumps(content, sort_keys=True, ensure_ascii=False).encode()).hexdigest()

    def get(self, key):
        if key is None:
            return None
        with self.lock:
            found = self.items.pop(key, None)
            if found is None or self.clock() - found[0] >= self.ttl:
                return None
            self.items[key] = found
            return found[1]

    def put(self, key, result):
        if key is not None:
            with self.lock:
                self.items.pop(key, None)
                self.items[key] = (self.clock(), result)
                while len(self.items) > self.capacity:
                    self.items.popitem(last=False)


def install(app, embedding_loader, reranker_loader):
    admission = GATE
    cache = DocumentCache()

    async def execute(request, kind):
        key = os.environ.get('ORBISOPS_RETRIEVAL_API_KEY', '')
        if not key or request.headers.get('Authorization') != 'Bearer ' + key:
            raise HTTPException(401, 'UNAUTHORIZED')
        raw = await request.body()
        if not 1 <= len(raw) <= 128000:
            raise HTTPException(413, 'INPUT_SIZE_INVALID')
        try:
            data = await request.json()
        except (ValueError, UnicodeDecodeError):
            raise HTTPException(400, 'JSON_OBJECT_REQUIRED')
        model_name, revision = validate(data, kind)
        cache_key = cache.key(data, kind)
        cached = cache.get(cache_key)
        if cached is not None:
            return cached
        if not admission.acquire(blocking=False, purpose='orbisops_' + kind):
            raise HTTPException(503, 'RETRIEVAL_BUSY')

        def infer():
            try:
                model = embedding_loader() if kind == 'embedding' else reranker_loader()
                assert_loaded_revision(model, revision, kind)
                result = {'model': model_name, 'revision': revision, 'preprocessing': PREPROCESS}
                if kind == 'embedding':
                    prompt = INSTRUCTION if data['kind'] == 'query' else "Represent the user's input."
                    inputs = [{'text': normalize_text(data['text'])}]
                    length = inspect_encoder_inputs(model, inputs, prompt)
                    if not length['fits']:
                        raise HTTPException(413, {'code': 'MODEL_INPUT_TOO_LONG', **length})
                    values = model.encode(inputs, prompt=prompt,
                                          truncate_dim=1024, normalize_embeddings=True)[0].tolist()
                    norm = math.sqrt(sum(float(v) ** 2 for v in values))
                    if len(values) != 1024 or not math.isfinite(norm) or norm == 0:
                        raise HTTPException(503, 'EMBEDDING_RESULT_INVALID')
                    result['embedding'] = [float(v) / norm for v in values]
                else:
                    import torch
                    pairs = [(normalize_text(data['query']), {'text': normalize_text(d['text'])})
                             for d in data['documents']]
                    values = bounded_rerank(model, pairs, torch.nn.Identity())
                    if len(values) != len(pairs) or not all(math.isfinite(float(v)) for v in values):
                        raise HTTPException(503, 'RERANK_RESULT_INVALID')
                    result['ranking'] = [{'id': d['id'], 'score': float(v)}
                                         for d, v in zip(data['documents'], values)]
                cache.put(cache_key, result)
                return result
            finally:
                admission.release()
        return await run_in_threadpool(infer)

    @app.post('/orbisops/embed')
    async def embed(request: Request):
        return await execute(request, 'embedding')

    @app.post('/orbisops/rerank')
    async def rerank(request: Request):
        return await execute(request, 'reranker')
