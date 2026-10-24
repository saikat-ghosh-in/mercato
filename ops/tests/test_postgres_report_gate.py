import importlib.util
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("postgres_reports", ROOT / "scripts" / "assert-postgres-reports.py")
reports = importlib.util.module_from_spec(spec)
spec.loader.exec_module(reports)
TEMP_ROOT = ROOT / "backend" / "target" / "ops-test-tmp"
TEMP_ROOT.mkdir(parents=True, exist_ok=True)


class RequiredPostgresReportsTests(unittest.TestCase):
    def reports(self, directory):
        for name, methods in reports.REQUIRED.items():
            suite = ET.Element("testsuite", name=name, tests=str(len(methods)), failures="0", errors="0", skipped="0")
            for method in methods: ET.SubElement(suite, "testcase", name=method)
            ET.ElementTree(suite).write(Path(directory) / f"TEST-{name}.xml")

    def test_all_required_schema_and_concurrency_methods_must_pass(self):
        with tempfile.TemporaryDirectory(dir=TEMP_ROOT) as directory:
            self.reports(directory)
            reports.verify(directory)

    def test_absent_skipped_or_failed_postgres_evidence_fails_the_build(self):
        with tempfile.TemporaryDirectory(dir=TEMP_ROOT) as directory:
            with self.assertRaises(ValueError): reports.verify(directory)
            for tag in ("skipped", "failure", "error"):
                self.reports(directory)
                path = Path(directory) / "TEST-com.mercato.jobs.PostgresMigrationTest.xml"
                suite = ET.parse(path)
                ET.SubElement(suite.getroot().find("testcase"), tag)
                suite.write(path)
                with self.assertRaises(ValueError): reports.verify(directory)


if __name__ == "__main__": unittest.main()
