"""Exercise the Linux release installer without networking or real service changes."""
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / "redeploy.sh"


@unittest.skipUnless(os.name == "posix" and shutil.which("flock"), "Linux deployment tooling required")
class RedeployTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.app = self.root / "app"
        (self.app / "config").mkdir(parents=True)
        (self.app / "config/cookie-release-ready").touch()
        (self.app / "mercato-backend.jar").write_bytes(b"old-artifact")
        self.payload = self.root / "release.jar"
        self.payload.write_bytes(b"new-artifact" * 100000)
        self.manifest = self.root / "checksum"
        self.manifest.write_text(hashlib.sha256(self.payload.read_bytes()).hexdigest() + "  mercato-backend.jar\n")
        self.bin = self.root / "bin"
        self.bin.mkdir()
        stub = '''#!/usr/bin/env python3
import os,sys,pathlib
name=pathlib.Path(sys.argv[0]).name
args=sys.argv[1:]
if name=='curl':
 if args[-1].endswith('/api/auth/session'):
  print('{"authenticated":false,"contextId":"checked-session"}')
 else:
  source=os.environ['FIXTURE_CHECKSUM'] if args[-1].endswith('.sha256') else os.environ['FIXTURE_JAR']
  pathlib.Path(args[args.index('--output')+1]).write_bytes(pathlib.Path(source).read_bytes())
elif name=='sudo':
 os.execvp(args[1],args[1:])
elif name=='systemctl':
 with open(os.environ['FIXTURE_CALLS'],'a') as f:f.write(' '.join(args)+'\\n')
 if args[0]=='restart' and os.environ.get('FIXTURE_FAIL_RESTART'):
  marker=pathlib.Path(os.environ['FIXTURE_FAIL_RESTART'])
  if marker.exists():
   marker.unlink()
   sys.exit(1)
'''
        for name in ["curl", "sudo", "systemctl"]:
            path = self.bin / name
            path.write_text(stub)
            path.chmod(0o755)
        self.env = dict(os.environ, PATH=str(self.bin) + os.pathsep + os.environ["PATH"],
                        MERCATO_APP_DIR=str(self.app), MERCATO_SITE="https://example.test",
                        FIXTURE_JAR=str(self.payload), FIXTURE_CHECKSUM=str(self.manifest),
                        FIXTURE_CALLS=str(self.root / "calls"))

    def deploy(self, tag="v9.8.7"):
        return subprocess.run(["bash", str(SCRIPT), tag], env=self.env, capture_output=True, text=True, timeout=15)

    def assert_untouched(self):
        self.assertEqual((self.app / "mercato-backend.jar").read_bytes(), b"old-artifact")
        self.assertFalse((self.root / "calls").exists())

    def test_rejects_invalid_tag_before_downloading(self):
        self.assertNotEqual(self.deploy("../main").returncode, 0)
        self.assert_untouched()

    def test_unconfigured_cutover_cannot_replace_artifact(self):
        (self.app / "config/cookie-release-ready").unlink()
        self.assertNotEqual(self.deploy().returncode, 0)
        self.assert_untouched()

    def test_failed_restart_can_retry_the_same_verified_release(self):
        marker = self.root / "restart-fails-once"
        marker.touch()
        self.env["FIXTURE_FAIL_RESTART"] = str(marker)
        self.assertNotEqual(self.deploy().returncode, 0)
        self.assertFalse((self.app / "releases/current-tag").exists())
        retry = self.deploy()
        self.assertEqual(retry.returncode, 0, retry.stdout + retry.stderr)
        self.assertEqual((self.app / "releases/current-tag").read_text().strip(), "v9.8.7")

    def test_checksum_failure_retains_installed_artifact(self):
        self.manifest.write_text("0" * 64 + "  mercato-backend.jar\n")
        self.assertNotEqual(self.deploy().returncode, 0)
        self.assert_untouched()

    def test_manifest_cannot_name_another_file(self):
        self.manifest.write_text("0" * 64 + "  ../../different-file\n")
        self.assertNotEqual(self.deploy().returncode, 0)
        self.assert_untouched()

    def test_verified_release_installs_and_retains_previous_artifact(self):
        result = self.deploy()
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual((self.app / "mercato-backend.jar").read_bytes(), self.payload.read_bytes())
        previous = list((self.app / "releases").glob("previous-*.jar"))
        self.assertEqual(len(previous), 1)
        self.assertEqual(previous[0].read_bytes(), b"old-artifact")
        self.assertEqual((self.app / "releases/current-tag").read_text().strip(), "v9.8.7")
        self.assertIn("restart mercato", (self.root / "calls").read_text())
        repeated = self.deploy()
        self.assertEqual(repeated.returncode, 0, repeated.stdout + repeated.stderr)
        self.assertEqual(len(list((self.app / "releases").glob("previous-*.jar"))), 1)

    def test_reused_tag_with_different_content_cannot_replace_artifact(self):
        self.assertEqual(self.deploy().returncode, 0)
        original = (self.app / "mercato-backend.jar").read_bytes()
        self.payload.write_bytes(b"changed-artifact" * 100000)
        self.manifest.write_text(hashlib.sha256(self.payload.read_bytes()).hexdigest() + "  mercato-backend.jar\n")
        self.assertNotEqual(self.deploy().returncode, 0)
        self.assertEqual((self.app / "mercato-backend.jar").read_bytes(), original)
