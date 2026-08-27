import ast
import asyncio
import unittest
import time
from unittest.mock import Mock
from patch_embedding_dimensions import patch


class HttpError(Exception):
    def __init__(self, status_code, detail): self.status_code=status_code


class DimensionsPatchTest(unittest.TestCase):
    def function(self):
        source='''async def endpoint(payload):
    texts = ['synthetic']
    vectors = encode_items(texts, payload.get("input_type") or EMBEDDING_DEFAULT_INPUT_TYPE)
    return vectors
async def media(payload,items,requested_dimension):
    vectors = encode_items(items, str(payload.get("input_type") or EMBEDDING_DEFAULT_INPUT_TYPE), requested_dimension)
    return vectors
async def rerank(pairs):
    scores = rerank_model().predict(pairs)
    return scores
async def preload(models):
    errors = preload_all_models(models)
    return errors
def preload_all_models(models: list[str] | None = None) -> dict[str, str]:
    return {}
def response(vectors):
    return {
        "usage": {"prompt_tokens": 0, "total_tokens": 0},
    }
'''
        encode=Mock(return_value=[[1]])
        namespace={'encode_items':encode,'EMBEDDING_DEFAULT_INPUT_TYPE':'document','EMBEDDING_DIMENSIONS':2048,'HTTPException':HttpError}
        exec(compile(ast.parse(patch(source)),'patched-provider','exec'),namespace)
        self.namespace=namespace
        return namespace['endpoint'],encode

    def test_requested_dimensions_reach_encoder(self):
        endpoint,encode=self.function();asyncio.run(endpoint({'dimensions':1024}))
        encode.assert_called_once_with(['synthetic'],'document',1024)

    def test_legacy_default_remains_2048(self):
        endpoint,encode=self.function();asyncio.run(endpoint({}))
        encode.assert_called_once_with(['synthetic'],'document',2048)

    def test_invalid_dimensions_do_not_run_model(self):
        for value in [None,True,'1024',0,63,2049,3.5]:
            endpoint,encode=self.function()
            with self.assertRaises(HttpError):asyncio.run(endpoint({'dimensions':value}))
            encode.assert_not_called()

    def test_source_drift_does_not_guess_patch(self):
        with self.assertRaises(ValueError):patch('unknown source')

    def test_busy_rerank_does_not_evaluate_loader_before_admission(self):
        from fastapi import HTTPException
        from qwen_inference_admission import GATE, REQUEST_DEADLINE
        self.function()
        loader=Mock(side_effect=AssertionError('Must not load before admission'))
        self.namespace['rerank_model']=loader
        GATE.acquire()
        token = REQUEST_DEADLINE.set(time.monotonic() + .02)
        try:
            with self.assertRaises(HTTPException) as error:
                asyncio.run(self.namespace['rerank']([['query','doc']]))
            self.assertEqual(503,error.exception.status_code)
            loader.assert_not_called()
        finally:
            REQUEST_DEADLINE.reset(token)
            GATE.release()

    def test_native_route_passes_complete_pairs_to_bounded_inference(self):
        from types import SimpleNamespace
        self.function()
        model = SimpleNamespace(predict=Mock(return_value=SimpleNamespace(tolist=lambda: [0.75])))
        self.namespace['rerank_model'] = lambda: model
        self.assertEqual([0.75, 0.75], asyncio.run(self.namespace['rerank']([['q', 'a'], ['q', 'b']])))
        self.assertEqual([[['q', 'a']], [['q', 'b']]], [call.args[0] for call in model.predict.call_args_list])

    def test_native_prediction_failure_releases_shared_slot(self):
        from qwen_inference_admission import GATE
        from types import SimpleNamespace
        self.function()
        self.namespace['rerank_model'] = lambda: SimpleNamespace(predict=Mock(side_effect=ValueError('failed inference')))
        with self.assertRaises(ValueError):
            asyncio.run(self.namespace['rerank']([['q', 'a']]))
        self.assertFalse(GATE.snapshot()['busy'])

if __name__=='__main__':unittest.main()
