import asyncio
import time
import unittest
from types import SimpleNamespace
from fastapi import HTTPException
from orbisops_retrieval_contract import validate, assert_loaded_revision, EMBEDDING, EMBEDDING_REVISION, RERANKER, RERANKER_REVISION, PREPROCESS


class ContractTest(unittest.TestCase):
    def test_wrong_identity_is_not_echoed_as_success(self):
        with self.assertRaises(HTTPException) as caught:
            validate({'model': 'wrong'}, 'embedding')
        self.assertEqual(400, caught.exception.status_code)

    def test_embedding_dimension_and_kind_are_fixed(self):
        body = dict(model=EMBEDDING, revision=EMBEDDING_REVISION, preprocessing=PREPROCESS,
                    dimensions=1024, kind='query', text='查询故障')
        self.assertEqual((EMBEDDING, EMBEDDING_REVISION), validate(body, 'embedding'))
        with self.assertRaises(HTTPException):
            validate(dict(body, dimensions=2048), 'embedding')

    def test_duplicate_and_excessive_candidates_are_rejected(self):
        body = dict(model=RERANKER, revision=RERANKER_REVISION, preprocessing=PREPROCESS,
                    query='故障', documents=[{'id': 'a', 'text': '排查'}])
        self.assertEqual((RERANKER, RERANKER_REVISION), validate(body, 'reranker'))
        for docs in [body['documents'] * 2, [{'id': str(i), 'text': '排查'} for i in range(21)]]:
            with self.assertRaises(HTTPException):
                validate(dict(body, documents=docs), 'reranker')

    def test_actual_loaded_revision_is_required(self):
        model = SimpleNamespace(model=SimpleNamespace(config=SimpleNamespace(_commit_hash=RERANKER_REVISION)))
        assert_loaded_revision(model, RERANKER_REVISION, 'reranker')
        with self.assertRaises(HTTPException) as caught:
            assert_loaded_revision(model, '0' * 40, 'reranker')
        self.assertEqual(503, caught.exception.status_code)


class DocumentCacheTest(unittest.TestCase):
    def test_exact_contract_keys_expiry_capacity_and_query_exclusion(self):
        from orbisops_retrieval_contract import DocumentCache
        now = [0]
        cache = DocumentCache(capacity=2, ttl=10, clock=lambda: now[0])
        data = dict(model=EMBEDDING, revision=EMBEDDING_REVISION, preprocessing=PREPROCESS,
                    dimensions=1024, kind='document', text='Redis')
        first = cache.key(data, 'embedding')
        self.assertNotEqual(first, cache.key(dict(data, text='MySQL'), 'embedding'))
        self.assertNotEqual(first, cache.key(dict(data, revision='0'*40), 'embedding'))
        self.assertIsNone(cache.key(dict(data, kind='query'), 'embedding'))
        self.assertIsNone(cache.key({}, 'reranker'))
        cache.put(first, {'embedding': [1]})
        self.assertEqual({'embedding': [1]}, cache.get(first))
        cache.put('second', {})
        cache.put('third', {})
        self.assertIsNone(cache.get(first))
        now[0] = 10
        self.assertIsNone(cache.get('third'))


class BoundedRerankTest(unittest.TestCase):
    def test_native_route_keeps_default_scoring_and_stops_after_inflight_pair(self):
        from orbisops_retrieval_contract import bounded_native_rerank
        now, calls = [0], []

        def predict(pairs, **kwargs):
            self.assertEqual({'batch_size': 1}, kwargs)
            calls.append(pairs)
            now[0] += 6
            return SimpleNamespace(tolist=lambda: [0.75])

        with self.assertRaises(HTTPException) as caught:
            bounded_native_rerank(lambda: SimpleNamespace(predict=predict), list(range(20)), clock=lambda: now[0], budget_seconds=5)
        self.assertEqual('RERANK_BUDGET_EXCEEDED', caught.exception.detail)
        self.assertEqual([[0]], calls)

    def test_native_fast_route_preserves_all_scores_in_input_order(self):
        from orbisops_retrieval_contract import bounded_native_rerank
        model = SimpleNamespace(predict=lambda pairs, **kw: SimpleNamespace(tolist=lambda: [float(pairs[0])]))
        self.assertEqual([0.2, 0.9], bounded_native_rerank(lambda: model, [0.2, 0.9], clock=lambda: 0))

    def test_cold_model_loading_consumes_the_same_budget_before_dispatch(self):
        from orbisops_retrieval_contract import bounded_native_rerank
        from unittest.mock import Mock
        now = [0]
        model = SimpleNamespace(predict=Mock(side_effect=AssertionError('Expired load must not start a kernel')))
        def load():
            now[0] = 6
            return model
        with self.assertRaises(HTTPException) as caught:
            bounded_native_rerank(load, [['q', 'd']], clock=lambda: now[0], budget_seconds=5)
        self.assertEqual('RERANK_BUDGET_EXCEEDED', caught.exception.detail)
        model.predict.assert_not_called()

    def test_native_route_uses_its_own_budget_without_changing_operational_contract(self):
        from orbisops_retrieval_contract import bounded_native_rerank, bounded_rerank
        now = [0]
        def predict(pairs, **options):
            now[0] += 6
            return SimpleNamespace(tolist=lambda: [float(pairs[0])])
        model = SimpleNamespace(predict=predict)
        self.assertEqual([0.2, 0.9], bounded_native_rerank(lambda: model, [0.2, 0.9], clock=lambda: now[0]))
        now[0] = 0
        with self.assertRaises(HTTPException):
            bounded_rerank(model, [0.2, 0.9], None, clock=lambda: now[0])

    def test_slow_model_does_not_keep_scoring_twenty_documents_after_timeout(self):
        from orbisops_retrieval_contract import bounded_rerank
        now, calls = [0], []

        def predict(pairs, **kwargs):
            calls.append(pairs)
            self.assertEqual(1, kwargs['batch_size'])
            now[0] += 3
            return SimpleNamespace(tolist=lambda: [1.0])

        with self.assertRaises(HTTPException) as caught:
            bounded_rerank(SimpleNamespace(predict=predict), list(range(20)), None, clock=lambda: now[0])
        self.assertEqual('RERANK_BUDGET_EXCEEDED', caught.exception.detail)
        self.assertEqual([[0], [1]], calls)

    def test_fast_model_keeps_all_scores_and_order(self):
        from orbisops_retrieval_contract import bounded_rerank
        model = SimpleNamespace(predict=lambda pairs, **kw: SimpleNamespace(tolist=lambda: [float(pairs[0])]))
        self.assertEqual([2.0, -1.0, 3.0], bounded_rerank(model, [2, -1, 3], None, clock=lambda: 0))

    def test_late_or_invalid_last_score_does_not_return_partial_success(self):
        from orbisops_retrieval_contract import bounded_rerank
        for value in [[], [1, 2], [float('nan')]]:
            model = SimpleNamespace(predict=lambda *a, **k: SimpleNamespace(tolist=lambda: value))
            with self.assertRaises(HTTPException):
                bounded_rerank(model, ['one'], None, clock=lambda: 0)
        times = iter([0, 0, 6])
        model = SimpleNamespace(predict=lambda *a, **k: SimpleNamespace(tolist=lambda: [1]))
        with self.assertRaises(HTTPException):
            bounded_rerank(model, ['one'], None, clock=lambda: next(times))


class RerankResponseDeadlineTest(unittest.IsolatedAsyncioTestCase):
    async def test_http_deadline_leaves_inflight_worker_alive(self):
        from orbisops_retrieval_contract import await_rerank
        finish = asyncio.Event()
        async def infer():
            await finish.wait()
            raise HTTPException(503, 'RERANK_BUDGET_EXCEEDED')

        worker = asyncio.create_task(infer())
        with self.assertRaises(HTTPException) as caught:
            await await_rerank(worker, time.monotonic() + 0.01)
        self.assertEqual('RERANK_BUDGET_EXCEEDED', caught.exception.detail)
        self.assertFalse(worker.done())
        finish.set()
        with self.assertRaises(HTTPException):
            await worker

    async def test_success_propagates_scores(self):
        from orbisops_retrieval_contract import await_rerank
        worker = asyncio.get_running_loop().create_future()
        worker.set_result({'ranking': [1.5, -2]})
        self.assertEqual({'ranking': [1.5, -2]}, await await_rerank(worker, time.monotonic() + 1))


if __name__ == '__main__':
    unittest.main()
