#!/usr/bin/env python3
"""Private pgBackRest operations. Nothing runs on import; no shell evaluation."""
import argparse
import configparser
from datetime import datetime, timezone
import hashlib
import json
import math
import os
from pathlib import Path
import re
import shlex
import shutil
import stat
import subprocess
import sys
import time
from urllib.parse import urlparse
from urllib.request import urlopen
import uuid


class PolicyError(RuntimeError):
    pass


def utcnow():
    return datetime.now(timezone.utc)


def instant(value):
    try:
        result = datetime.fromisoformat(value.replace("Z", "+00:00"))
        if result.tzinfo is None: raise ValueError()
        return result.astimezone(timezone.utc)
    except (ValueError, TypeError, AttributeError):
        raise PolicyError("A valid timezone-qualified evidence timestamp is required") from None


def read_json(path):
    try: return json.loads(Path(path).read_text(encoding="utf-8"))
    except (OSError, ValueError): raise PolicyError("Required evidence is missing or invalid") from None


def write_json(path, value):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    # Exclusive creation prevents an old proof from being silently overwritten.
    with path.open("x", encoding="utf-8") as output:
        os.chmod(path, 0o600)
        json.dump(value, output, indent=2)


def required_capacity(evidence):
    fields = [evidence.get(key) for key in ("fullBytes", "maxDifferentialBytes", "dailyWalBytes")]
    if any(type(value) is not int or value <= 0 for value in fields):
        raise PolicyError("Positive measured full/differential/WAL capacity is required")
    full, differential, wal = fields
    # Four weekly chains, the next full, all daily differentials, 35 WAL days,
    # and 20% headroom. Retention is never reduced in response to low space.
    return math.ceil((5 * full + 28 * differential + 35 * wal) * 1.20)


def validate_capacity(evidence, now, local_free_bytes):
    age = (now - instant(evidence.get("observedAt"))).total_seconds()
    if not 0 <= age <= 30 * 86400: raise PolicyError("Capacity evidence is stale or in the future")
    required = required_capacity(evidence)
    if type(evidence.get("repositoryBudgetBytes")) is not int or evidence["repositoryBudgetBytes"] < required:
        raise PolicyError("Repository capacity does not cover four chains plus headroom")
    if local_free_bytes < max(512 * 1024**2, 2 * evidence["dailyWalBytes"]):
        raise PolicyError("Local WAL filesystem has insufficient free space")
    return required


def validate_bucket(response, namespace, bucket):
    data = response.get("data", {})
    if data.get("namespace") != namespace or data.get("name") != bucket:
        raise PolicyError("OCI returned a different backup bucket")
    if data.get("public-access-type") != "NoPublicAccess" or data.get("storage-tier") != "Standard":
        raise PolicyError("Backup bucket must be private and immediately retrievable")


def repository_health(info, stanza, now):
    try:
        data = next(item for item in info if item["name"] == stanza)
        if data["status"]["code"] != 0 or data["cipher"] != "aes-256-cbc": raise ValueError()
        backups = [item for item in data["backup"] if not item.get("error", False)]
        latest = max(backups, key=lambda item: item["timestamp"]["stop"])
        full = max((item for item in backups if item["type"] == "full"), key=lambda item: item["timestamp"]["stop"])
        latest_age = now.timestamp() - latest["timestamp"]["stop"]
        full_age = now.timestamp() - full["timestamp"]["stop"]
        if not 0 <= latest_age <= 26 * 3600 or not 0 <= full_age <= 8 * 86400: raise ValueError()
        return latest
    except (KeyError, ValueError, TypeError, StopIteration):
        raise PolicyError("Encrypted repository needs a complete backup within 26h and full backup within 8d") from None


def safe_restore_target(target, root, production):
    target, root, production = Path(target), Path(root).resolve(), Path(production).resolve()
    resolved = target.resolve()
    if target.exists() or target.is_symlink() or resolved == root or not resolved.is_relative_to(root):
        raise PolicyError("Restore target must be a new directory inside the dedicated drill root")
    if resolved == production or resolved.is_relative_to(production) or production.is_relative_to(resolved):
        raise PolicyError("Restore target overlaps the production cluster")
    if target.parent.resolve() != root:
        raise PolicyError("Restore target must be a direct child; nested/symlink paths are forbidden")
    return resolved


def validate_restore_evidence(evidence):
    required = ("markerMatches", "databaseChecksPassed", "archiveModeOff", "offInstance", "keysRecoveredSeparately")
    if any(evidence.get(key) is not True for key in required):
        raise PolicyError("Restore, private isolation, and independent key recovery must be verified")
    rpo = (instant(evidence.get("simulatedFailureAt")) - instant(evidence.get("probeCommittedAt"))).total_seconds()
    rto = (instant(evidence.get("completedAt")) - instant(evidence.get("drillStartedAt"))).total_seconds()
    if not instant(evidence.get("drillStartedAt")) <= instant(evidence.get("simulatedFailureAt")) <= instant(evidence.get("completedAt")):
        raise PolicyError("Restore evidence has inconsistent failure/recovery chronology")
    if not 0 <= rpo <= 900 or not 0 <= rto <= 4 * 3600:
        raise PolicyError("Restore evidence misses RPO 15m or RTO 4h")
    return {**evidence, "rpoSeconds": rpo, "rtoSeconds": rto}


ENV_KEYS = {
    "PGBACKREST_CONFIG", "PGBACKREST_STANZA", "PGBACKREST_REPO1_S3_KEY", "PGBACKREST_REPO1_S3_KEY_SECRET",
    "PGBACKREST_REPO1_CIPHER_PASS", "OCI_NAMESPACE", "OCI_BUCKET", "OCI_REGION", "OCI_AUTH",
    "PGDATA", "PGDATABASE", "PGHOST", "PGPORT", "PGBIN", "CAPACITY_EVIDENCE", "KEY_RECOVERY_EVIDENCE",
    "BACKUP_HEARTBEAT_URL", "WAL_HEARTBEAT_URL", "DRILL_ROOT", "STATE_DIRECTORY", "MERCATO_ENVIRONMENT",
}


def parse_environment(path):
    result = {}
    for line in Path(path).read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#"): continue
        key, separator, value = line.partition("=")
        if not separator or key not in ENV_KEYS or key in result: raise PolicyError("Unrecognized or duplicate backup setting")
        if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'": value = value[1:-1]
        result[key] = value
    return result


def validate_config(path, settings):
    config = configparser.ConfigParser(interpolation=None)
    if not config.read(path): raise PolicyError("pgBackRest configuration is missing")
    required = {"repo1-type": "s3", "repo1-cipher-type": "aes-256-cbc", "repo1-storage-verify-tls": "y",
                "repo1-retention-full": "4", "repo1-retention-full-type": "count",
                "repo1-retention-archive": "4", "repo1-retention-archive-type": "full",
                "repo1-s3-uri-style": "path", "archive-async": "n", "archive-check": "y"}
    for key, value in required.items():
        if config.get("global", key, fallback=None) != value: raise PolicyError("Unsafe encryption, transport, WAL or retention policy")
    try:
        if not 0 < config.getint("global", "archive-timeout") <= 180: raise ValueError()
    except (ValueError, configparser.Error): raise PolicyError("Remote WAL verification must complete within 180s") from None
    stanza = settings["PGBACKREST_STANZA"]
    if set(config.sections()) != {"global", stanza}: raise PolicyError("Use one explicitly reviewed stanza per configuration")
    if config.get(stanza, "pg1-path", fallback=None) != settings["PGDATA"]: raise PolicyError("Configured production data paths differ")
    if any(key.startswith("repo") for key in config[stanza]): raise PolicyError("Stanza repository overrides are forbidden")
    allowed_repository = set(required) | {"repo1-path", "repo1-s3-region", "repo1-s3-bucket", "repo1-s3-endpoint"}
    if any(key.startswith("repo") and key not in allowed_repository for key in config["global"]):
        raise PolicyError("Unreviewed repository overrides are forbidden")
    endpoint = f'{settings["OCI_NAMESPACE"]}.compat.objectstorage.{settings["OCI_REGION"]}.oraclecloud.com'
    for key, value in (("repo1-s3-endpoint", endpoint), ("repo1-s3-region", settings["OCI_REGION"]), ("repo1-s3-bucket", settings["OCI_BUCKET"])):
        if config.get("global", key, fallback=None) != value: raise PolicyError("S3 repository does not match the reviewed OCI bucket")
    if config.get("global", "repo1-path", fallback="") != f'/mercato/{settings["MERCATO_ENVIRONMENT"]}':
        raise PolicyError("Use a separate stage/production repository prefix")
    return config


def perform_backup(kind, stanza, command, notify, validate, now):
    if kind not in ("full", "diff"): raise PolicyError("Only scheduled full/differential backups are supported")
    started = now() if callable(now) else now
    notify("start")
    try:
        validate()
        command("check")
        command("backup", f"--type={kind}")
        command("check")
        latest = repository_health(command("info", "--output=json"), stanza, now() if callable(now) else now)
        if latest["timestamp"]["stop"] < math.floor(started.timestamp()):
            raise PolicyError("This run did not produce a newly completed backup")
        command("verify", f'--set={latest["label"]}')
        notify("success")
        return latest
    except Exception:
        notify("fail")
        raise


class Runtime:
    def __init__(self, env_file):
        path = Path(env_file)
        if path.is_symlink() or not path.is_file(): raise PolicyError("Backup settings must be a regular protected file")
        mode = path.stat()
        if os.name == "posix" and (mode.st_uid != 0 or stat.S_IMODE(mode.st_mode) & 0o137):
            raise PolicyError("Backup settings must be root-owned and at most mode 0640")
        self.settings = parse_environment(path)
        if ENV_KEYS - self.settings.keys(): raise PolicyError("Backup settings are incomplete")
        if any(not value or "REPLACE" in value or "CHANGE_ME" in value for value in self.settings.values()):
            raise PolicyError("Replace every example setting before running operations")
        if self.settings["MERCATO_ENVIRONMENT"] not in ("stg", "prod"): raise PolicyError("Explicit stage/production isolation is required")
        if not re.fullmatch(r"[a-zA-Z0-9_-]+", self.settings["PGBACKREST_STANZA"]): raise PolicyError("Invalid stanza")
        if len(self.settings["PGBACKREST_REPO1_CIPHER_PASS"]) < 32: raise PolicyError("Use an independently stored strong encryption passphrase")
        self.config = validate_config(self.settings["PGBACKREST_CONFIG"], self.settings)
        self.environment = {key: value for key, value in os.environ.items() if not key.startswith("PGBACKREST_")}
        self.environment.update({key: value for key, value in self.settings.items() if key.startswith("PGBACKREST_") or key in ("PGDATABASE", "PGHOST", "PGPORT")})
        self.environment["PATH"] = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
        self.empty_include = Path(self.settings["PGBACKREST_CONFIG"]).parent / "empty.d"
        if not self.empty_include.is_dir() or any(self.empty_include.iterdir()): raise PolicyError("The dedicated config include directory must exist and stay empty")

    def execute(self, args, timeout=240, output_json=False, environment=None):
        try:
            completed = subprocess.run(args, check=True, capture_output=True, text=True, timeout=timeout, env=environment or self.environment)
            return json.loads(completed.stdout) if output_json else completed.stdout.strip()
        except (subprocess.SubprocessError, OSError, ValueError):
            # Never include argv, stderr, URLs or secret values in public logs.
            raise PolicyError(f"{Path(args[0]).name} operation failed; inspect protected service logs") from None

    def pgbackrest(self, command, *options, timeout=None):
        args = ["pgbackrest", f'--config={self.settings["PGBACKREST_CONFIG"]}', f"--config-include-path={self.empty_include}",
                f'--stanza={self.settings["PGBACKREST_STANZA"]}', "--repo=1"]
        args += [command, *options] if command == "archive-push" else [*options, command]
        limit = 3 * 3600 if command in ("backup", "verify", "restore") else 240
        return self.execute(args, timeout=min(limit, timeout) if timeout is not None else limit, output_json=command == "info")

    def psql(self, sql, host=None, port=None):
        args = [str(Path(self.settings["PGBIN"]) / "psql"), "-X", "-v", "ON_ERROR_STOP=1", "-Atq", "-h", host or self.settings["PGHOST"],
                "-p", str(port or self.settings["PGPORT"]), "-U", "postgres", "-d", self.settings["PGDATABASE"], "-c", sql]
        return self.execute(args, timeout=30)

    def notify(self, key, state):
        url = self.settings[key]
        parsed = urlparse(url)
        if parsed.scheme != "https" or parsed.hostname != "hc-ping.com" or parsed.username or parsed.query or parsed.fragment:
            raise PolicyError("Use a private HTTPS Healthchecks.io ping URL")
        suffix = {"start": "/start", "fail": "/fail", "success": ""}[state]
        try:
            with urlopen(url.rstrip("/") + suffix, timeout=8) as response:
                if response.status != 200: raise OSError()
        except Exception:
            # A failed ping must not prevent a backup; the remote dead-man check
            # detects missing success independently of this machine and Brevo.
            print("Backup monitor delivery failed", file=sys.stderr)

    def validate_policy(self, local_path=None, check_postgres=True):
        version = re.search(r"(\d+)\.(\d+)", self.execute(["pgbackrest", "version"]))
        if not version or tuple(map(int, version.groups())) < (2, 60): raise PolicyError("pgBackRest 2.60+ with repository verify is required")
        bucket = self.execute(["oci", "os", "bucket", "get", "--auth", self.settings["OCI_AUTH"], "--namespace-name", self.settings["OCI_NAMESPACE"],
                               "--name", self.settings["OCI_BUCKET"], "--region", self.settings["OCI_REGION"], "--output", "json"], output_json=True)
        validate_bucket(bucket, self.settings["OCI_NAMESPACE"], self.settings["OCI_BUCKET"])
        validate_capacity(read_json(self.settings["CAPACITY_EVIDENCE"]), utcnow(), shutil.disk_usage(local_path or self.settings["PGDATA"]).free)
        if check_postgres:
            postgres = json.loads(self.psql("SELECT jsonb_build_object('mode',current_setting('archive_mode'),'timeout',(SELECT setting::int FROM pg_settings WHERE name='archive_timeout'),'walLevel',current_setting('wal_level'))::text"))
            if postgres.get("mode") != "on" or not 0 < postgres.get("timeout", 0) <= 300 or postgres.get("walLevel") not in ("replica", "logical"):
                raise PolicyError("Continuous WAL requires archive_mode=on, replica WAL, and archive_timeout <=300s")
        proof = read_json(self.settings["KEY_RECOVERY_EVIDENCE"])
        fingerprint = hashlib.sha256(self.settings["PGBACKREST_REPO1_CIPHER_PASS"].encode()).hexdigest()
        if proof.get("cipherPassSha256") != fingerprint or proof.get("recoveredSeparately") is not True:
            raise PolicyError("Independent encryption-key recovery evidence is missing")
        if not 0 <= (utcnow() - instant(proof.get("verifiedAt"))).total_seconds() <= 90 * 86400:
            raise PolicyError("Key recovery must be rehearsed at least every 90 days")

    def monitor(self):
        try:
            self.validate_policy()
            self.pgbackrest("check")
            repository_health(self.pgbackrest("info", "--output=json"), self.settings["PGBACKREST_STANZA"], utcnow())
            # check forces a WAL switch and verifies that the segment reached
            # the configured remote repository, including on an idle database.
            self.notify("WAL_HEARTBEAT_URL", "success")
        except Exception:
            self.notify("WAL_HEARTBEAT_URL", "fail")
            raise

    def prepare_drill(self, output):
        self.validate_policy()
        marker = uuid.uuid4().hex
        restore_point = "mercato_drill_" + marker
        row = self.psql("INSERT INTO backup_probe.pitr_marker(marker,source_machine_id,counts) VALUES (" +
                        f"'{marker}','{machine_id()}',jsonb_build_object('users',(SELECT count(*) FROM users),'orders',(SELECT count(*) FROM order_snapshot),'payments',(SELECT count(*) FROM payments),'refunds',(SELECT count(*) FROM refunds))) " +
                        "RETURNING jsonb_build_object('marker',marker,'probeCommittedAt',committed_at,'sourceMachineId',source_machine_id,'counts',counts)::text")
        evidence = json.loads(row)
        self.psql(f"SELECT pg_create_restore_point('{restore_point}')")
        self.pgbackrest("check")
        evidence.update(restorePoint=restore_point, simulatedFailureAt=utcnow().isoformat())
        write_json(output, evidence)

    def restore_drill(self, probe_file, target, started_at, output):
        probe = read_json(probe_file)
        marker, restore_point = probe.get("marker", ""), probe.get("restorePoint", "")
        if not re.fullmatch(r"[a-f0-9]{32}", marker) or restore_point != "mercato_drill_" + marker: raise PolicyError("Invalid restore probe")
        if not re.fullmatch(r"[a-fA-F0-9]{32}", probe.get("sourceMachineId", "")) or probe["sourceMachineId"] == machine_id(): raise PolicyError("Restore drill must run on a different machine")
        if not 0 <= (utcnow() - instant(started_at)).total_seconds() <= 4 * 3600: raise PolicyError("End-to-end drill start must include provisioning/key recovery and be within 4h")
        def remaining():
            seconds = 4 * 3600 - (utcnow() - instant(started_at)).total_seconds()
            if seconds <= 0: raise PolicyError("End-to-end RTO budget was exhausted")
            return max(1, int(seconds))
        target = safe_restore_target(Path(target), Path(self.settings["DRILL_ROOT"]), Path(self.settings["PGDATA"]))
        self.validate_policy(local_path=target.parent, check_postgres=False)
        target.mkdir(mode=0o700)
        self.pgbackrest("restore", f"--pg1-path={target}", "--type=name", f"--target={restore_point}", "--target-action=promote", "--archive-mode=off", timeout=remaining())
        socket = target / "drill-socket"
        socket.mkdir(mode=0o700)
        hba = target / "drill_hba.conf"
        hba.write_text("local all all peer\nhost all all 0.0.0.0/0 reject\nhost all all ::/0 reject\n", encoding="utf-8")
        config = target / "drill_postgresql.conf"
        config.write_text("# Isolated drill. Production configuration is intentionally not loaded.\n", encoding="utf-8")
        options = shlex.join(["-c", f"config_file={config}", "-c", f"data_directory={target}", "-c", f"hba_file={hba}", "-c", f"unix_socket_directories={socket}",
                              "-c", "listen_addresses=", "-c", "primary_conninfo=", "-c", "primary_slot_name=", "-c", "shared_preload_libraries=",
                              "-c", "archive_mode=off", "-c", "ssl=off", "-p", "55432"])
        ctl = str(Path(self.settings["PGBIN"]) / "pg_ctl")
        try:
            self.execute([ctl, "-D", str(target), "-l", str(target / "drill.log"), "-o", options, "-t", str(remaining()), "-w", "start"], timeout=remaining())
            deadline = time.monotonic() + remaining()
            while self.psql("SELECT pg_is_in_recovery()", str(socket), 55432) != "f":
                if time.monotonic() > deadline: raise PolicyError("PITR did not reach its named target")
                time.sleep(2)
            row = json.loads(self.psql(f"SELECT jsonb_build_object('marker',marker,'counts',counts)::text FROM backup_probe.pitr_marker WHERE marker='{marker}'", str(socket), 55432))
            counts = json.loads(self.psql("SELECT jsonb_build_object('users',(SELECT count(*) FROM users),'orders',(SELECT count(*) FROM order_snapshot),'payments',(SELECT count(*) FROM payments),'refunds',(SELECT count(*) FROM refunds))::text", str(socket), 55432))
            proof = read_json(self.settings["KEY_RECOVERY_EVIDENCE"])
            evidence = {**probe, "drillStartedAt": started_at, "completedAt": utcnow().isoformat(),
                        "restoreMachineId": machine_id(), "offInstance": True,
                        "keysRecoveredSeparately": proof.get("recoveryMachineId") == machine_id() and instant(proof.get("verifiedAt")) >= instant(started_at),
                        "markerMatches": row.get("marker") == marker and row.get("counts") == probe.get("counts"),
                        "databaseChecksPassed": all(counts.get(key, -1) >= value for key, value in probe["counts"].items()),
                        "archiveModeOff": self.psql("SHOW archive_mode", str(socket), 55432) == "off"}
            write_json(output, validate_restore_evidence(evidence))
        finally:
            self.execute([ctl, "-D", str(target), "-m", "fast", "-w", "stop"], timeout=60)


def machine_id():
    value = Path("/etc/machine-id").read_text(encoding="utf-8").strip()
    if not re.fullmatch(r"[a-fA-F0-9]{32}", value): raise PolicyError("A stable machine ID is required for off-instance evidence")
    return value


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--env-file", default="/etc/mercato/backup.env")
    commands = parser.add_subparsers(dest="command", required=True)
    backup = commands.add_parser("backup")
    backup.add_argument("kind", choices=("full", "diff"))
    commands.add_parser("monitor")
    commands.add_parser("preflight")
    commands.add_parser("stanza-create")
    archive = commands.add_parser("archive-push")
    archive.add_argument("wal_path")
    probe = commands.add_parser("prepare-drill")
    probe.add_argument("--allow-probe-write", action="store_true", required=True)
    probe.add_argument("--output", required=True)
    restore = commands.add_parser("restore-drill")
    restore.add_argument("--probe", required=True)
    restore.add_argument("--target", required=True)
    restore.add_argument("--started-at", required=True)
    restore.add_argument("--output", required=True)
    gate = commands.add_parser("gate")
    gate.add_argument("--evidence", required=True)
    args = parser.parse_args()
    if args.command == "gate":
        proof = validate_restore_evidence(read_json(args.evidence))
        print(json.dumps({"rpoSeconds": proof["rpoSeconds"], "rtoSeconds": proof["rtoSeconds"], "restoreGate": "passed"}))
        return
    runtime = Runtime(args.env_file)
    if args.command == "backup": perform_backup(args.kind, runtime.settings["PGBACKREST_STANZA"], runtime.pgbackrest, lambda state: runtime.notify("BACKUP_HEARTBEAT_URL", state), runtime.validate_policy, utcnow)
    elif args.command == "monitor": runtime.monitor()
    elif args.command == "preflight": runtime.validate_policy()
    elif args.command == "stanza-create": runtime.validate_policy(check_postgres=False); runtime.pgbackrest("stanza-create")
    elif args.command == "archive-push":
        wal = Path(args.wal_path)
        if not wal.is_absolute(): wal = Path(runtime.settings["PGDATA"]) / wal
        wal = wal.resolve()
        if not wal.is_relative_to((Path(runtime.settings["PGDATA"]) / "pg_wal").resolve()): raise PolicyError("WAL path is outside the configured cluster")
        runtime.pgbackrest("archive-push", str(wal))
    elif args.command == "prepare-drill": runtime.prepare_drill(args.output)
    elif args.command == "restore-drill": runtime.restore_drill(args.probe, args.target, args.started_at, args.output)


if __name__ == "__main__":
    try: main()
    except (PolicyError, KeyError, OSError, ValueError, configparser.Error):
        print("Backup operation or release gate failed; inspect protected configuration and operator evidence", file=sys.stderr)
        sys.exit(1)
