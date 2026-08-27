"""Attach exact encoder provenance to its own vectors, never to historical vectors.

The existing bounded encode worker calls this wrapper while holding admission.
Only scalar certification is retained; the profile endpoint never loads a model.
Unknown provenance disables reuse and leaves the ordinary embedding contract intact.
"""
from copy import deepcopy
import hashlib
import importlib.metadata
import json
import os
from pathlib import Path
import re
import struct
import threading

from fastapi import HTTPException

SCHEMA = 'qwen-encoding-v1'
PIPELINE_FILES = ('app.py', 'qwen_encoding_identity.py', 'qwen_embedding_length_guard.py',
                  'qwen_cpu_attention.py', 'qwen_cpu_runtime.py')


def digest(value):
    return hashlib.sha256(json.dumps(value, ensure_ascii=False, sort_keys=True,
        separators=(',', ':'), allow_nan=False).encode()).hexdigest()


class CertifiedVectors(list):
    def __init__(self, vectors, identity):
        super().__init__(vectors)
        self.encoding_identity = deepcopy(identity)


def certify_profile(source, model, input_type, dimension):
    module = model._first_module()
    underlying = getattr(module, 'auto_model', None)
    revision = getattr(getattr(underlying, 'config', None), '_commit_hash', None)
    if not isinstance(revision, str) or not re.fullmatch(r'[0-9a-f]{40}', revision):
        raise ValueError('Actual loaded model revision is unavailable')
    tokenizer = module.tokenizer
    backend = getattr(tokenizer, 'backend_tokenizer', None)
    if backend is None:
        raise ValueError('Actual tokenizer content is unavailable')
    maximum = module.max_seq_length
    if not isinstance(maximum, int) or maximum < 1:
        raise ValueError('Actual encoder token limit is unavailable')
    processor = getattr(module, 'processor', None)
    # These are the actual prompt and processor values of this loaded encoder.
    prompt_state = {'prompts': model.prompts, 'defaultPromptName': model.default_prompt_name,
                    'chatTemplate': getattr(processor, 'chat_template', None),
                    'tokenizerChatTemplate': getattr(tokenizer, 'chat_template', None),
                    'processorClass': type(processor).__name__,
                    'moduleClass': type(module).__name__, 'maxTokens': maximum}
    path = Path(__file__).resolve().parent
    pipeline = {name: hashlib.sha256((path / name).read_bytes()).hexdigest()
                for name in PIPELINE_FILES}
    packages = {name: importlib.metadata.version(name)
                for name in ('torch', 'transformers', 'sentence-transformers', 'tokenizers')}
    import torch
    profile = {'model': source.EMBEDDING_MODEL_ID, 'loaded_revision': revision,
               'tokenizer_sha256': hashlib.sha256(backend.to_str().encode()).hexdigest(),
               'preprocessor_sha256': digest(prompt_state), 'max_tokens': maximum,
               'pipeline_sha256': digest(pipeline), 'packages': packages,
               'device': str(getattr(model, 'device', 'unavailable')),
               'parameter_dtype': str(getattr(underlying, 'dtype', 'unavailable')),
               'cpu_attention': os.getenv('INFERENCE_CPU_ATTENTION', 'native'),
               'cpu_threads': torch.get_num_threads(), 'interop_threads': torch.get_num_interop_threads(),
               'input_type': input_type, 'dimensions': dimension,
               'normalize': source.EMBEDDING_NORMALIZE, 'truncation': 'reject-over-limit',
               'resize': 'prefix-float32-renormalize-if-enabled-v1'}
    return {'schema': SCHEMA, 'profile_sha256': digest(profile), 'profile': profile}


class IdentityRegistry:
    def __init__(self, source, certify=certify_profile):
        self.source = source
        self.certify = certify
        self.lock = threading.Lock()
        self.profiles = {}
        self.generations = {}

    def encode(self, original, items, input_type='document', requested_dimension=None):
        normalized = (input_type or 'document').lower()
        dimension = requested_dimension or self.source.EMBEDDING_DIMENSIONS
        key = (normalized, dimension)
        with self.lock:
            self.profiles.pop(key, None)
            self.generations.pop(key, None)
        profile = None
        if normalized in ('query', 'document') and all(isinstance(item, str) for item in items):
            try:
                profile = self.certify(self.source, self.source.embedding_model(), normalized, dimension)
            except (AttributeError, ValueError, TypeError, OSError, importlib.metadata.PackageNotFoundError):
                # Do not stamp a configured revision onto an unknown encoder.
                profile = None
        vectors = original(items, input_type, requested_dimension)
        if profile is None:
            return CertifiedVectors(vectors, None)
        inputs = [{'index': index, 'input_sha256': hashlib.sha256(item.encode()).hexdigest(),
                   'vector_sha256': hashlib.sha256(struct.pack('<' + 'f' * len(vector), *vector)).hexdigest()}
                  for index, (item, vector) in enumerate(zip(items, vectors))]
        if len(vectors) != len(items):
            raise ValueError('Encoder returned a different number of vectors')
        with self.lock:
            self.profiles[key] = deepcopy(profile)
            self.generations[key] = self.source.model_state_snapshot()['embedding'].get('loadedAt')
        return CertifiedVectors(vectors, {**profile, 'inputs': inputs})

    def current(self, input_type, dimensions):
        # State is a scalar snapshot and does not acquire/load/retain a model.
        state = self.source.model_state_snapshot()['embedding']
        if state.get('status') != 'loaded' or state.get('resident') is not True:
            raise HTTPException(503, 'ENCODING_IDENTITY_UNAVAILABLE')
        with self.lock:
            profile = deepcopy(self.profiles.get((input_type, dimensions)))
            generation = self.generations.get((input_type, dimensions))
        if profile is None or profile['profile']['loaded_revision'] != state.get('loadedRevision'):
            raise HTTPException(503, 'ENCODING_IDENTITY_UNAVAILABLE')
        if generation is None or generation != state.get('loadedAt'):
            raise HTTPException(503, 'ENCODING_IDENTITY_UNAVAILABLE')
        return profile


def install(source):
    registry = IdentityRegistry(source)
    original = source.encode_items
    source.encode_items = lambda items, input_type='document', requested_dimension=None: registry.encode(
        original, items, input_type, requested_dimension)

    @source.app.get('/v1/embedding-identity')
    async def identity(input_type: str = 'document', dimensions: int = 1024):
        if input_type not in ('document', 'query') or not 64 <= dimensions <= 2048:
            raise HTTPException(400, 'Unsupported encoder profile')
        return registry.current(input_type, dimensions)

    return registry
