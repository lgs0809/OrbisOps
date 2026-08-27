#!/usr/bin/env python3
from pathlib import Path
import runpy
import unittest

closed = runpy.run_path(str(Path(__file__).with_name('inspect-auto-skill-publication.py')))['closed_before_atomic_publication']


class AtomicInspectionTest(unittest.TestCase):
    def setUp(self):
        self.release = dict(status='ROLLED_BACK', version=0, reason='POLICY_PUBLICATION_BASELINE_STALE:SKILL_ATOMIC_SOURCE_NOT_ACTIVE')
        self.candidate = dict(status='ROLLED_BACK', reason=self.release['reason'], versionCount=0)

    def test_precommit_baseline_rejection_is_not_mistaken_for_a_committed_replacement(self):
        self.assertTrue(closed(self.release, self.candidate, [], []))

    def test_any_publication_footprint_still_requires_full_atomic_checks(self):
        self.assertFalse(closed(self.release, self.candidate, [dict(status='ROLLED_BACK')], []))
        self.assertFalse(closed(self.release, self.candidate, [], [dict(role='TARGET')]))
        self.assertFalse(closed(self.release, dict(self.candidate, versionCount=1), [], []))
        self.assertFalse(closed(dict(self.release, version=1), self.candidate, [], []))

    def test_open_or_unexplained_failures_cannot_pass_as_a_safe_precommit_close(self):
        self.assertFalse(closed(self.release, dict(self.candidate, status='ACTIVE'), [], []))
        self.assertFalse(closed(self.release, dict(self.candidate, reason='different'), [], []))
        self.assertFalse(closed(dict(self.release, reason='network'), dict(self.candidate, reason='network'), [], []))


chain = runpy.run_path(str(Path(__file__).with_name('inspect-auto-skill-publication.py')))['superseding_publication_chain']

class HistoricalPublicationInspectionTest(unittest.TestCase):
    def setUp(self):
        self.old=dict(skillId='s',version=2,candidateId='c2',releaseId='r2',skillHash='h2',packageHash='p2',runtimeVersion=3,runtimeHash='h3',runtimePackage='p3',runtimeGenerationId='g3')
        self.new=dict(skillId='s',version=3,candidateId='c3',releaseId='r3',skillHash='h3',packageHash='p3',status='ACTIVE',canaryPercent=0)
        self.candidates=[dict(candidateId='c3',status='ACTIVE',baseVersion=2,baseHash='h2')]
        self.versions=[dict(skillId='s',version=2,candidateId='c2',skillHash='h2',packageHash='p2'),dict(skillId='s',version=3,candidateId='c3',skillHash='h3',packageHash='p3',sourceType='SKILL_EVOLVER',bodyHash='body3',storedBodyHash='body3')]
        self.index=[dict(generationId='g3',skillId='s',version=3,hash='h3',packageHash='p3',status='READY',dimension=1024)]
    def check_chain(self):
        return chain(self.old,[self.old,self.new],self.candidates,self.versions,self.index)
    def test_later_exact_authorized_publication_keeps_historical_receipt_valid(self):
        self.assertEqual(['r3'],self.check_chain())
    def test_later_version_without_exact_base_fence_is_not_proof(self):
        self.candidates[0]['baseHash']='unrelated'
        self.assertIsNone(self.check_chain())
        self.candidates[0]['baseHash']='h2';self.candidates[0]['baseVersion']=1
        self.assertIsNone(self.check_chain())
    def test_missing_or_wrong_native_current_index_is_not_proof(self):
        for field,value in [('status','PENDING'),('generationId','other'),('dimension',3),('hash','other')]:
            old=self.index[0][field];self.index[0][field]=value
            self.assertIsNone(self.check_chain());self.index[0][field]=old
    def test_missing_transition_or_tampered_body_cannot_pass(self):
        last=self.versions.pop();self.assertIsNone(self.check_chain());self.versions.append(last)
        self.versions[-1]['bodyHash']='tampered';self.assertIsNone(self.check_chain())


if __name__ == '__main__':
    unittest.main()
