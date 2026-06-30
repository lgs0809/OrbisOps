import hashlib
import struct
import threading
import unittest
from types import SimpleNamespace

from fastapi import HTTPException
from qwen_encoding_identity import IdentityRegistry, certify_profile, digest


class EncodingIdentityTests(unittest.TestCase):
    def setUp(self):
        self.state = {'status': 'loaded', 'resident': True, 'loadedRevision': 'a' * 40,
                      'loadedAt': 'generation-one'}
        self.source = SimpleNamespace(EMBEDDING_DIMENSIONS=1024,
            embedding_model=lambda: object(), model_state_snapshot=lambda: {'embedding': dict(self.state)})
        def certify(source, model, kind, dimension):
            profile = {'loaded_revision': self.state['loadedRevision'], 'input_type': kind,
                       'dimensions': dimension, 'tokenizer_sha256': 'tokenizer-a'}
            return {'schema': 'qwen-encoding-v1', 'profile': profile, 'profile_sha256': digest(profile)}
        self.registry = IdentityRegistry(self.source, certify)

    def encode(self, text='原文不可截断', kind='document', dimension=1024):
        return self.registry.encode(lambda items, kind, dim: [[0.25, 0.75] for item in items],
                                    [text], kind, dimension)

    def unavailable(self, kind='document', dimension=1024):
        with self.assertRaises(HTTPException) as result:
            self.registry.current(kind, dimension)
        self.assertEqual(503, result.exception.status_code)

    def test_uncertified_profile_never_uses_configured_model_identity(self):
        self.unavailable()

    def test_actual_input_and_returned_float32_vector_are_bound(self):
        vectors = self.encode()
        item = vectors.encoding_identity['inputs'][0]
        self.assertEqual(hashlib.sha256('原文不可截断'.encode()).hexdigest(), item['input_sha256'])
        self.assertEqual(hashlib.sha256(struct.pack('<ff', 0.25, 0.75)).hexdigest(), item['vector_sha256'])
        self.assertEqual(0, item['index'])

    def test_dimension_and_input_type_cannot_reuse_another_profile(self):
        self.encode()
        self.unavailable('query')
        self.unavailable(dimension=2048)

    def test_profile_unknown_after_missing_loaded_revision(self):
        self.encode()
        self.state['loadedRevision'] = None
        self.unavailable()

    def test_eviction_does_not_reload_or_retain_an_encoder(self):
        self.encode()
        self.state.update(status='evicted', resident=False)
        self.source.embedding_model = lambda: self.fail('The identity reader must never load a model')
        self.unavailable()

    def test_reloaded_same_revision_requires_new_certification(self):
        self.encode()
        self.state['loadedAt'] = 'generation-two'
        self.unavailable()
        self.encode()
        self.assertEqual('a' * 40, self.registry.current('document', 1024)['profile']['loaded_revision'])

    def test_return_metadata_cannot_mutate_the_registry(self):
        vectors = self.encode()
        vectors.encoding_identity['profile']['loaded_revision'] = 'spoofed'
        returned = self.registry.current('document', 1024)
        returned['profile']['dimensions'] = 12
        self.assertEqual(1024, self.registry.current('document', 1024)['profile']['dimensions'])

    def test_unknown_tokenizer_disables_reuse_without_breaking_embedding(self):
        self.encode()
        def unknown(*args):
            raise ValueError('Unknown tokenizer')
        self.registry.certify = unknown
        vectors = self.encode()
        self.assertEqual([[0.25, 0.75]], vectors)
        self.assertIsNone(vectors.encoding_identity)
        self.unavailable()

    def test_failed_kernel_cannot_publish_identity(self):
        self.encode()
        def failed(*args):
            raise RuntimeError('Kernel failed')
        with self.assertRaises(RuntimeError):
            self.registry.encode(failed, ['unchanged'], 'document', 1024)
        self.unavailable()

    def test_missing_output_cannot_publish_identity(self):
        with self.assertRaises(ValueError):
            self.registry.encode(lambda *args: [], ['original'], 'document', 1024)
        self.unavailable()

    def test_later_request_metadata_does_not_replace_the_first_vectors_identity(self):
        first = self.encode('first', dimension=64)
        second = self.encode('second', dimension=128)
        self.assertEqual(64, first.encoding_identity['profile']['dimensions'])
        self.assertEqual(128, second.encoding_identity['profile']['dimensions'])
        self.assertNotEqual(first.encoding_identity['inputs'][0]['input_sha256'],
                            second.encoding_identity['inputs'][0]['input_sha256'])

    def test_concurrent_return_objects_keep_their_own_input_and_profile(self):
        barrier = threading.Barrier(2)
        results = {}
        def worker(text, dimension):
            def encode(*args):
                barrier.wait(timeout=5)
                return [[0.25, 0.75]]
            results[text] = self.registry.encode(encode, [text], 'document', dimension)
        workers = [threading.Thread(target=worker, args=(text, dim)) for text, dim in [('a', 64), ('b', 128)]]
        for worker in workers: worker.start()
        for worker in workers: worker.join(timeout=6)
        self.assertEqual(2, len(results))
        for text, dimension in [('a', 64), ('b', 128)]:
            identity = results[text].encoding_identity
            self.assertEqual(dimension, identity['profile']['dimensions'])
            self.assertEqual(hashlib.sha256(text.encode()).hexdigest(), identity['inputs'][0]['input_sha256'])

    def test_untrusted_revision_fails_certification_before_tokenizer_access(self):
        model = SimpleNamespace(_first_module=lambda: SimpleNamespace(auto_model=SimpleNamespace(
            config=SimpleNamespace(_commit_hash=None))))
        with self.assertRaises(ValueError):
            certify_profile(self.source, model, 'document', 1024)


if __name__ == '__main__':
    unittest.main()
