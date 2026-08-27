#!/usr/bin/env python3
"""Offline deployment idempotence and ownership-aware rollback retention checks."""
import importlib.util
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]


def load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


deployment = load('retrieval_deployment', ROOT / 'scripts/deploy-existing-retrieval-contract.py')
patcher = load('retrieval_patch', ROOT / 'deploy/acceptance/patch_embedding_dimensions.py')


def backup(identity, created, **changes):
    value = {'Id': identity, 'Name': '/embedding-model-before-orbisops-contract-' + str(created),
             'Created': str(created), 'State': {'Status': 'exited'},
             'Mounts': [{'Type': 'volume', 'Name': 'embedding-model-cache', 'Destination': '/models'}]}
    value.update(changes)
    return value


class RetentionTest(unittest.TestCase):
    def test_immediate_rollback_is_pinned_even_when_it_is_the_oldest(self):
        rows = [backup('rollback', 1), backup('newer', 3), backup('other', 2)]
        self.assertEqual({'newer', 'other'},
                         {x['Id'] for x in deployment.obsolete_backups(rows, 1, {'rollback'})})

    def test_larger_limit_keeps_newest_additional_backup(self):
        rows = [backup('rollback', 1), backup('newer', 3), backup('other', 2)]
        self.assertEqual(['other'],
                         [x['Id'] for x in deployment.obsolete_backups(rows, 2, {'rollback'})])

    def test_running_foreign_names_and_foreign_mounts_are_never_selected(self):
        rows = [backup('running', 1, State={'Status': 'running'}),
                backup('unknown', 2, Name='/embedding-model-before-orbisops-contract-user-backup'),
                backup('business', 3, Mounts=[{'Type': 'volume', 'Name': 'business-db', 'Destination': '/models'}]),
                backup('bind', 4, Mounts=[{'Type': 'bind', 'Source': '/somewhere', 'Destination': '/models'}]),
                backup('none', 5, Mounts=[])]
        self.assertEqual([], deployment.obsolete_backups(rows, 1))

    def test_retention_is_stable_after_pruning(self):
        rows = [backup('old', 1), backup('current', 2)]
        removed = {x['Id'] for x in deployment.obsolete_backups(rows, 1)}
        self.assertEqual({'old'}, removed)
        self.assertEqual([], deployment.obsolete_backups([x for x in rows if x['Id'] not in removed], 1))


class InstrumentationTest(unittest.TestCase):
    def original(self):
        return '\n'.join([
            patcher.BEFORE,
            'vectors = encode_items(items, str(payload.get("input_type") or EMBEDDING_DEFAULT_INPUT_TYPE), requested_dimension)',
            'scores = rerank_model().predict(pairs)',
            '        "usage": {"prompt_tokens": 0, "total_tokens": 0},',
            'errors = preload_all_models(models)',
            'def preload_all_models(models: list[str] | None = None) -> dict[str, str]:',
        ])

    def test_repeated_build_does_not_double_instrument(self):
        once = patcher.patch(self.original())
        self.assertEqual(once, patcher.patch(once))
        self.assertEqual(1, once.count('from starlette.concurrency import run_in_threadpool'))
        self.assertEqual(1, once.count('def preload_all_models('))

    def test_partial_and_changed_instrumentation_fail_closed(self):
        once = patcher.patch(self.original())
        for changed in [once[:-1], once.replace('bounded_native_rerank(rerank_model, pairs)', 'unknown_inference(pairs)')]:
            with self.subTest(source=changed[-40:]):
                with self.assertRaises(ValueError):
                    patcher.patch(changed)

    def test_duplicate_original_call_fails_closed(self):
        with self.assertRaises(ValueError):
            patcher.patch(self.original() + '\n' + patcher.BEFORE)


if __name__ == '__main__':
    unittest.main()
