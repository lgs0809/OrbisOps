"""Synthetic evidence-contract cases, not actual rollback or model acceptance."""
import hashlib
from pathlib import Path
import runpy
import unittest

check=runpy.run_path(str(Path(__file__).with_name('verify-skill-group-rollback.py')))['original_package_actually_read']
class Contract(unittest.TestCase):
    def setUp(self):
        self.source=dict(skillId='original',version=1,packageHash='package')
        self.ref=dict(self.source,artifactHashes={'SKILL.md':hashlib.sha256(b'full body').hexdigest()})
        self.catalog=[{'skillCatalogRefs':[self.ref]}]
        self.read=dict(self.source,paths=['SKILL.md'])
    def test_body_only_original_requires_no_nonexistent_extra_file(self):
        self.assertTrue(check([self.source],self.catalog,[self.read],[]))
    def test_declared_resource_must_really_be_read(self):
        self.ref['artifactHashes']['resources/method.json']='hash'
        self.assertFalse(check([self.source],self.catalog,[self.read],[]))
        self.assertTrue(check([self.source],self.catalog,[self.read,dict(self.source,paths=['SKILL.md','resources/method.json'])],[]))
    def test_catalog_membership_is_not_a_body_read(self):
        self.assertFalse(check([self.source],self.catalog,[],[]))
    def test_wrong_version_or_package_does_not_count(self):
        self.assertFalse(check([self.source],self.catalog,[dict(self.read,version=2)],[]))
        self.assertFalse(check([self.source],self.catalog,[dict(self.read,packageHash='other')],[]))
    def test_injected_full_body_requires_exact_bytes_and_frozen_identity(self):
        self.assertTrue(check([self.source],self.catalog,[],[dict(self.source,body='full body')]))
        self.assertFalse(check([self.source],self.catalog,[],[dict(self.source,body='partial')]))
        self.assertFalse(check([self.source],self.catalog,[],[dict(self.source,version=2,body='full body')]))
    def test_missing_manifest_fails_closed(self):
        self.ref.pop('artifactHashes');self.assertFalse(check([self.source],self.catalog,[self.read],[]))

if __name__=='__main__':unittest.main()
