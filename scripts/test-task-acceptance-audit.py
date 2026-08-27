#!/usr/bin/env python3
"""Guard against reporting empty or stale persisted acceptance as a passing audit."""
from pathlib import Path
import runpy
import unittest

assess = runpy.run_path(str(Path(__file__).with_name('inspect-task-acceptance.py')))['assess']


class AcceptanceAuditTest(unittest.TestCase):
    def current(self, outcome='SUCCEEDED'):
        return [dict(kind='episode', revision=2, outcome=outcome, verifiedRef='a-2'),
                dict(kind='acceptance', id='a-2', revision=2, outcome=outcome,
                     checks=3, hashMatches=True)]

    def test_empty_or_unaccepted_episode_is_not_exercised(self):
        for rows in ([], self.current()[:1]):
            self.assertEqual('NOT_EXERCISED', assess(rows)[0])

    def test_current_success_and_failure_both_have_separate_integrity(self):
        for outcome in ('SUCCEEDED', 'FAILED'):
            self.assertEqual(('PASS_INTEGRITY', []), assess(self.current(outcome)))

    def test_historical_acceptance_does_not_validate_new_revision(self):
        rows = self.current()
        rows[0]['revision'] = 3
        self.assertEqual('NOT_CURRENT', assess(rows)[0])
        rows[0]['verifiedRef'] = ''
        self.assertEqual('NOT_CURRENT', assess(rows)[0])

    def test_corruption_empty_checks_and_outcome_mismatch_fail(self):
        for field, value in (('hashMatches', False), ('checks', 0), ('outcome', 'FAILED')):
            rows = self.current()
            rows[1][field] = value
            self.assertEqual('FAIL_INTEGRITY', assess(rows)[0])
        rows = self.current() + [dict(kind='receipt', hashMatches=False)]
        self.assertEqual('FAIL_INTEGRITY', assess(rows)[0])


if __name__ == '__main__':
    unittest.main()
