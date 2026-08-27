#!/usr/bin/env python3
"""Reference identity checks only; no service or database mutation."""
from pathlib import Path
import runpy
import unittest

matches = runpy.run_path(str(Path(__file__).with_name('inspect-mcp-discovery.py')))['receipt_citation_matches']


class ReceiptCitationTest(unittest.TestCase):
    def setUp(self):
        self.hashes = {'provider': 'provider-hash', 'projection': 'projection-hash'}
        self.rows = [dict(evidenceId='ev-' + result, resultId=result, hash=digest,
                          runId='run', projectId='project', verified=1)
                     for result, digest in self.hashes.items()]

    def test_actual_provider_and_projection_references_both_resolve(self):
        for text in ('ev-provider', 'ev-projection', 'provider-hash', 'projection-hash'):
            self.assertTrue(matches(text, self.rows, self.hashes, 'run', 'project'))

    def test_foreign_scope_or_hash_or_unverified_reference_cannot_pass(self):
        for changed in (dict(runId='another'), dict(projectId='another'),
                        dict(hash='wrong'), dict(verified=0), dict(resultId='foreign')):
            rows = [{**self.rows[0], **changed}]
            self.assertFalse(matches('ev-provider provider-hash', rows, self.hashes, 'run', 'project'))

    def test_invented_reference_and_unlinked_identifier_cannot_pass(self):
        self.assertFalse(matches('ev-invented', self.rows, self.hashes, 'run', 'project'))
        self.assertFalse(matches('ev-provider', [], self.hashes, 'run', 'project'))

    def test_natural_answer_can_use_the_verified_companion_evidence_without_printing_ids(self):
        displayed = [dict(evidenceId='ev-provider', toolResultId='provider', outputHash='provider-hash',
                          fullOutputRef='db:provider', projectId='project', runId='run')]
        self.assertTrue(matches('当前版本与状态见实际回执。', self.rows, self.hashes, 'run', 'project', displayed))
        for changed in (dict(runId='other'), dict(projectId='other'), dict(outputHash='wrong'),
                        dict(fullOutputRef='db:other'), dict(toolResultId='other')):
            self.assertFalse(matches('正常回答', self.rows, self.hashes, 'run', 'project', [{**displayed[0], **changed}]))

    def test_companion_cannot_hide_an_invented_inline_reference(self):
        displayed = [dict(evidenceId='ev-provider', toolResultId='provider', outputHash='provider-hash',
                          fullOutputRef='db:provider', projectId='project', runId='run')]
        self.assertFalse(matches('evidence-00000000-0000-0000-0000-000000000000', self.rows,
                                 self.hashes, 'run', 'project', displayed))


if __name__ == '__main__':
    unittest.main()
