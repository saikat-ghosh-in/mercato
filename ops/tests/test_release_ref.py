import importlib.util
from pathlib import Path
import unittest
ROOT=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('release_ref',ROOT/'ops'/'check_release_ref.py')
release_ref=importlib.util.module_from_spec(spec)
spec.loader.exec_module(release_ref)

class ReleaseRefTests(unittest.TestCase):
    def test_beta_prereleases_use_reviewed_staging_and_stable_releases_require_main(self):
        self.assertEqual('origin/stg',release_ref.required_base('v0.1.0-beta.2',True))
        self.assertEqual('origin/main',release_ref.required_base('v0.1.0',False))
    def test_mislabeled_or_unversioned_releases_are_rejected(self):
        for tag,prerelease in [('v0.1.0-beta.2',False),('v0.1.0',True),('latest',False),('v0.1.0;echo bad',False)]:
            with self.assertRaises(ValueError):release_ref.required_base(tag,prerelease)

if __name__=='__main__':unittest.main()
