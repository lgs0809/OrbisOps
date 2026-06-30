"""Correct the existing image's OpenAI dimensions argument; no model/default changes."""
from pathlib import Path

BEFORE = '''    vectors = encode_items(texts, payload.get("input_type") or EMBEDDING_DEFAULT_INPUT_TYPE)'''
AFTER = '''    requested_dimension = payload.get("dimensions", EMBEDDING_DIMENSIONS)
    if (isinstance(requested_dimension, bool) or not isinstance(requested_dimension, int)
            or not 64 <= requested_dimension <= 2048):
        raise HTTPException(status_code=400, detail="dimensions must be an integer between 64 and 2048")
    vectors = encode_items(texts, payload.get("input_type") or EMBEDDING_DEFAULT_INPUT_TYPE, requested_dimension)'''


def patch(source):
    if source.count(BEFORE) != 1:
        raise ValueError('Expected exactly one known text-embedding call; source drift requires review')
    source = source.replace(BEFORE, AFTER)
    # FastAPI awaits body parsing on the event loop; CPU inference belongs in its bounded worker pool.
    text_call = 'vectors = encode_items(texts, payload.get("input_type") or EMBEDDING_DEFAULT_INPUT_TYPE, requested_dimension)'
    media_call = 'vectors = encode_items(items, str(payload.get("input_type") or EMBEDDING_DEFAULT_INPUT_TYPE), requested_dimension)'
    rerank_call = 'scores = rerank_model().predict(pairs)'
    for call in (text_call, media_call, rerank_call):
        if source.count(call) != 1:
            raise ValueError('Expected existing local inference call; review source drift')
    source = source.replace(text_call, 'vectors = await run_in_threadpool(encode_items, texts, payload.get("input_type") or EMBEDDING_DEFAULT_INPUT_TYPE, requested_dimension)')
    source = source.replace(media_call, 'vectors = await run_in_threadpool(encode_items, items, str(payload.get("input_type") or EMBEDDING_DEFAULT_INPUT_TYPE), requested_dimension)')
    source = source.replace(rerank_call, 'scores = await run_in_threadpool(infer, lambda: rerank_model().predict(pairs))')
    usage = '        "usage": {"prompt_tokens": 0, "total_tokens": 0},'
    if source.count(usage) != 1:
        raise ValueError('Expected exactly one text embedding response; review source drift')
    source = source.replace(usage, '        "encoding_identity": getattr(vectors, "encoding_identity", None),\n' + usage)
    preload_call = 'errors = preload_all_models(models)'
    if source.count(preload_call) != 1:
        raise ValueError('Expected existing preload endpoint; review source drift')
    source = source.replace(preload_call, 'errors = await run_in_threadpool(preload_all_models, models)')
    definition = 'def preload_all_models(models: list[str] | None = None) -> dict[str, str]:'
    if source.count(definition) != 1:
        raise ValueError('Expected existing preload lifecycle; review source drift')
    source = source.replace(definition, definition.replace('preload_all_models', 'load_requested_models'))
    source += '\n\ndef preload_all_models(models=None):\n    return infer(load_requested_models, models)\n'
    return 'from starlette.concurrency import run_in_threadpool\nfrom qwen_inference_admission import infer\n' + source


if __name__ == '__main__':
    target = Path('/app/app.py')
    target.write_text(patch(target.read_text()))
