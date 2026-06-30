"""Reject whole text inputs that the installed encoder would otherwise truncate.

Uses the loaded Sentence Transformers module's own preprocessor and prompt selection,
including the model chat template. No approximate counter, weight download or model change.
"""
import hashlib
import json
import logging
import time
import uuid
from fastapi import HTTPException, Request
from starlette.concurrency import run_in_threadpool
from qwen_inference_admission import infer

LOGGER = logging.getLogger('uvicorn.error')


def inspect_inputs(model, texts, input_type='document'):
    if not isinstance(texts, list) or not texts or len(texts) > 64 or any(not isinstance(t, str) for t in texts):
        raise HTTPException(400, 'input must contain 1 to 64 text strings')
    if sum(len(t) for t in texts) > 2_000_000:
        raise HTTPException(413, 'text input exceeds preprocessing character budget')
    prompt_names = ('query',) if input_type == 'query' else ('document', 'passage', 'corpus')
    prompt_name = next((name for name in prompt_names if name in model.prompts), model.default_prompt_name)
    prompt = model.prompts.get(prompt_name) if prompt_name else None
    return inspect_encoder_inputs(model, texts, prompt, prompt_name)


def inspect_encoder_inputs(model, items, prompt, prompt_name=None):
    """Count the exact inputs/prompt passed to encode, including OrbisOps dictionary inputs."""
    module = model._first_module()
    maximum = module.max_seq_length
    if not isinstance(maximum, int) or not 1 <= maximum <= 1_000_000:
        raise HTTPException(503, 'MODEL_TOKEN_LIMIT_UNAVAILABLE')
    counts = []
    for item in items:
        features = module.preprocess([item], prompt=prompt,
            processing_kwargs={'text': {'truncation': False, 'padding': False},
                               'chat_template': {'truncation': False}})
        counts.append(int(features['input_ids'].shape[-1]))
    return {'tokenCounts': counts, 'maxTokens': maximum, 'promptName': prompt_name,
            'tokenizer': type(module.tokenizer).__name__, 'truncated': False,
            'fits': all(count <= maximum for count in counts)}


def install(source):
    original = source.encode_items

    def encode_complete_texts(items, input_type='document', requested_dimension=None):
        trace = uuid.uuid4().hex
        def encode():
            started = time.monotonic()
            LOGGER.info('RETRIEVAL_REQUEST %s', json.dumps({'requestId': trace, 'stage': 'START',
                'inputType': input_type, 'dimensions': requested_dimension, 'items': len(items)}))
            if all(isinstance(item, str) for item in items):
                report = inspect_inputs(source.embedding_model(), items, (input_type or 'document').lower())
                LOGGER.info('RETRIEVAL_REQUEST %s', json.dumps({'requestId': trace, 'stage': 'PREPROCESSED',
                    'inputType': input_type, 'dimensions': requested_dimension,
                    'inputSha256': [hashlib.sha256(item.encode()).hexdigest() for item in items],
                    'inputCharacters': [len(item) for item in items],
                    'inputBytes': [len(item.encode()) for item in items], **report,
                    'elapsedSeconds': round(time.monotonic() - started, 3)}))
                if not report['fits']:
                    raise HTTPException(413, {'code': 'MODEL_INPUT_TOO_LONG', **report})
            result = original(items, input_type, requested_dimension)
            LOGGER.info('RETRIEVAL_REQUEST %s', json.dumps({'requestId': trace, 'stage': 'COMPLETE',
                'elapsedSeconds': round(time.monotonic() - started, 3), 'outputItems': len(result) if result is not None else None}))
            return result
        return infer(encode, _admission_purpose='embedding:' + trace)

    source.encode_items = encode_complete_texts

    @source.app.post('/v1/embedding-token-count')
    async def token_count(request: Request):
        payload = await source.read_json_payload(request)
        text = payload.get('input')
        texts = text if isinstance(text, list) else [text]
        def count():
            return inspect_inputs(source.embedding_model(), texts, payload.get('input_type', 'document'))
        return await run_in_threadpool(infer, count)
