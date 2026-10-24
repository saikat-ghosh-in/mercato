import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from datetime import datetime, timezone, timedelta

SOURCE = Path(__file__).resolve().parents[1] / "backup_agent.py"
TEST_ROOT = SOURCE.parents[1] / "backend" / "target" / "ops-test-tmp"
TEST_ROOT.mkdir(parents=True, exist_ok=True)
agent = None
if SOURCE.exists():
    spec = importlib.util.spec_from_file_location("backup_agent", SOURCE)
    agent = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(agent)

NOW = datetime(2026, 10, 23, 12, tzinfo=timezone.utc)


class BackupPolicyTests(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(agent, "Backup policy implementation is missing")

    def capacity(self):
        return {"observedAt": NOW.isoformat(), "fullBytes": 100, "maxDifferentialBytes": 50,
                "dailyWalBytes": 20, "repositoryBudgetBytes": 4000, "keyRecoveryVerified": True}

    def test_capacity_reserves_four_chains_plus_next_full_and_wal_without_reducing_retention(self):
        self.assertEqual(agent.required_capacity(self.capacity()), 3120)
        agent.validate_capacity(self.capacity(), NOW, local_free_bytes=2 * 1024**3)
        for patch in ({"repositoryBudgetBytes": 3000}, {"fullBytes": 0}, {"dailyWalBytes": -1},
                      {"observedAt": (NOW - timedelta(days=31)).isoformat()}):
            with self.assertRaises(agent.PolicyError):
                agent.validate_capacity({**self.capacity(), **patch}, NOW, local_free_bytes=2 * 1024**3)
        with self.assertRaises(agent.PolicyError):
            agent.validate_capacity(self.capacity(), NOW, local_free_bytes=10)

    def test_only_the_expected_private_standard_oci_bucket_is_accepted(self):
        bucket = {"data": {"name": "mercato-prod", "namespace": "namespace", "public-access-type": "NoPublicAccess", "storage-tier": "Standard"}}
        agent.validate_bucket(bucket, "namespace", "mercato-prod")
        for patch in ({"public-access-type": "ObjectRead"}, {"storage-tier": "Archive"}, {"name": "other"}):
            with self.assertRaises(agent.PolicyError):
                agent.validate_bucket({"data": {**bucket["data"], **patch}}, "namespace", "mercato-prod")

    def test_backup_health_requires_a_recent_full_and_recent_complete_backup(self):
        info = [{"name": "mercato", "cipher": "aes-256-cbc", "status": {"code": 0}, "backup": [
            {"label": "full", "type": "full", "timestamp": {"stop": NOW.timestamp() - 7 * 86400}},
            {"label": "diff", "type": "diff", "timestamp": {"stop": NOW.timestamp() - 3600}},
        ]}]
        self.assertEqual(agent.repository_health(info, "mercato", NOW)["label"], "diff")
        info[0]["backup"][1]["timestamp"]["stop"] = NOW.timestamp() - 27 * 3600
        with self.assertRaises(agent.PolicyError): agent.repository_health(info, "mercato", NOW)

    def test_full_backup_age_and_repository_errors_fail_closed(self):
        for cipher, status, full_age in [("none", 0, 1), ("aes-256-cbc", 2, 1), ("aes-256-cbc", 0, 9)]:
            info = [{"name": "mercato", "cipher": cipher, "status": {"code": status}, "backup": [
                {"label": "full", "type": "full", "timestamp": {"stop": NOW.timestamp() - full_age * 86400}},
                {"label": "diff", "type": "diff", "timestamp": {"stop": NOW.timestamp() - 10}},
            ]}]
            with self.assertRaises(agent.PolicyError): agent.repository_health(info, "mercato", NOW)

    def test_restore_requires_a_new_child_of_the_dedicated_drill_root(self):
        with tempfile.TemporaryDirectory(dir=TEST_ROOT) as directory:
            root = Path(directory) / "drills"
            root.mkdir()
            production = Path(directory) / "production"
            production.mkdir()
            target = root / "drill-001"
            self.assertEqual(agent.safe_restore_target(target, root, production), target.resolve())
            for invalid in (root, production, root / ".." / "outside"):
                with self.assertRaises(agent.PolicyError): agent.safe_restore_target(invalid, root, production)
            target.mkdir()
            with self.assertRaises(agent.PolicyError): agent.safe_restore_target(target, root, production)

    def test_restore_symlinks_cannot_escape_the_dedicated_root(self):
        with tempfile.TemporaryDirectory(dir=TEST_ROOT) as directory:
            root = Path(directory) / "drills"
            root.mkdir()
            outside = Path(directory) / "outside"
            outside.mkdir()
            try: (root / "link").symlink_to(outside, target_is_directory=True)
            except OSError: self.skipTest("Host cannot create directory symlinks")
            with self.assertRaises(agent.PolicyError): agent.safe_restore_target(root / "link" / "new", root, outside)

    def test_restore_evidence_requires_actual_marker_and_rpo_rto_bounds(self):
        evidence = {"markerMatches": True, "databaseChecksPassed": True, "archiveModeOff": True,
                    "offInstance": True, "keysRecoveredSeparately": True,
                    "probeCommittedAt": (NOW - timedelta(minutes=4)).isoformat(),
                    "simulatedFailureAt": NOW.isoformat(),
                    "drillStartedAt": NOW.isoformat(), "completedAt": (NOW + timedelta(hours=2)).isoformat()}
        result = agent.validate_restore_evidence(evidence)
        self.assertEqual(result["rpoSeconds"], 240)
        self.assertEqual(result["rtoSeconds"], 7200)
        for patch in ({"markerMatches": False}, {"offInstance": False},
                      {"probeCommittedAt": (NOW - timedelta(minutes=16)).isoformat()},
                      {"completedAt": (NOW + timedelta(hours=5)).isoformat()}):
            with self.assertRaises(agent.PolicyError): agent.validate_restore_evidence({**evidence, **patch})
        with self.assertRaises(agent.PolicyError):
            agent.validate_restore_evidence({**evidence, "drillStartedAt": (NOW - timedelta(hours=4)).isoformat(), "completedAt": (NOW - timedelta(hours=3)).isoformat()})

    def test_no_success_heartbeat_if_backup_or_verification_fails(self):
        for failure in ("backup", "verify"):
            events = []
            def command(name, *options):
                events.append(name)
                if name == failure: raise agent.PolicyError("failed")
                if name == "info": return [{"name": "mercato", "cipher": "aes-256-cbc", "status": {"code": 0}, "backup": [{"label": "full", "type": "full", "timestamp": {"stop": NOW.timestamp()}}]}]
            with self.assertRaises(agent.PolicyError):
                agent.perform_backup("full", "mercato", command, lambda state: events.append(state), lambda: None, NOW)
            self.assertNotIn("success", events)
            self.assertEqual(events[-1], "fail")

    def test_success_requires_remote_check_backup_metadata_and_repository_verification(self):
        events = []
        def command(name, *options):
            events.append((name, options))
            if name == "info": return [{"name": "mercato", "cipher": "aes-256-cbc", "status": {"code": 0}, "backup": [{"label": "fresh-full", "type": "full", "timestamp": {"stop": NOW.timestamp()}}]}]
        agent.perform_backup("full", "mercato", command, lambda state: events.append(state), lambda: events.append("policy"), NOW)
        self.assertEqual(events[-1], "success")
        self.assertIn(("verify", ("--set=fresh-full",)), events)
        self.assertLess(events.index(("backup", ("--type=full",))), events.index(("verify", ("--set=fresh-full",))))

    def test_secret_environment_file_is_parsed_as_data_without_shell_evaluation(self):
        with tempfile.TemporaryDirectory(dir=TEST_ROOT) as directory:
            path = Path(directory) / "backup.env"
            path.write_text("PGBACKREST_REPO1_CIPHER_PASS='$(must-not-run)'\n", encoding="utf-8")
            self.assertEqual(agent.parse_environment(path)["PGBACKREST_REPO1_CIPHER_PASS"], "$(must-not-run)")
            path.write_text("UNREVIEWED_KEY=bad\n", encoding="utf-8")
            with self.assertRaises(agent.PolicyError): agent.parse_environment(path)

    def test_backup_freshness_uses_completion_time_after_a_long_upload(self):
        completed_at = NOW + timedelta(hours=1)
        clock = NOW
        events = []
        def command(name, *options):
            nonlocal clock
            if name == "backup": clock = completed_at
            if name == "info": return [{"name": "mercato", "cipher": "aes-256-cbc", "status": {"code": 0}, "backup": [{"label": "long-full", "type": "full", "timestamp": {"stop": completed_at.timestamp()}}]}]
        agent.perform_backup("full", "mercato", command, events.append, lambda: None, lambda: clock)
        self.assertEqual(events[-1], "success")

    def test_a_preexisting_healthy_backup_cannot_masquerade_as_this_completed_run(self):
        events = []
        def command(name, *options):
            if name == "info": return [{"name": "mercato", "cipher": "aes-256-cbc", "status": {"code": 0}, "backup": [{"label": "old-full", "type": "full", "timestamp": {"stop": NOW.timestamp() - 3600}}]}]
        with self.assertRaises(agent.PolicyError): agent.perform_backup("full", "mercato", command, events.append, lambda: None, NOW)
        self.assertNotIn("success", events)
        self.assertEqual(events[-1], "fail")

    def test_unreviewed_endpoint_and_slow_archive_policy_are_rejected(self):
        template = (SOURCE.parent / "pgbackrest" / "pgbackrest.conf.example").read_text(encoding="utf-8")
        template = template.replace("REPLACE_NAMESPACE", "namespace").replace("REPLACE_PRIVATE_BACKUP_BUCKET", "bucket")
        settings = {"PGBACKREST_STANZA": "mercato", "PGDATA": "/var/lib/postgresql/14/main", "OCI_NAMESPACE": "namespace", "OCI_REGION": "ap-mumbai-1", "OCI_BUCKET": "bucket", "MERCATO_ENVIRONMENT": "prod"}
        with tempfile.TemporaryDirectory(dir=TEST_ROOT) as directory:
            path = Path(directory) / "pgbackrest.conf"
            path.write_text(template, encoding="utf-8")
            agent.validate_config(path, settings)
            for changed in (template.replace("archive-timeout=180", "archive-timeout=1800"), template.replace("[mercato]", "repo1-storage-host=attacker.invalid\n[mercato]")):
                path.write_text(changed, encoding="utf-8")
                with self.assertRaises(agent.PolicyError): agent.validate_config(path, settings)


if __name__ == "__main__": unittest.main()
