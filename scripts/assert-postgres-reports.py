#!/usr/bin/env python3
"""Fail CI if required real-PostgreSQL/schema tests were skipped or absent."""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

REQUIRED = {
    "com.mercato.jobs.PostgresMigrationTest": {"freshAndBothPopulatedOciSchemasConvergeWithoutLosingLegacyData"},
    "com.mercato.jobs.PostgresDurableJobsTest": {"concurrentWorkersClaimDisjointJobs", "simultaneousEnqueueOfSameBusinessKeyIsIdempotent"},
    "com.mercato.Service.CashfreeFinalizeConcurrencyTest": {"captureSurvivesAllocationRollbackAndRefundIncludesAllCharges", "concurrentAllocationIsExactlyOnce"},
    "com.mercato.session.PostgresAuthConsumptionTest": {"resetTokenHasOneWinnerAndRevokesEveryIndexedSessionExactlyOnce", "recoveryCodeHasOneWinnerAcrossIndependentAdministratorSessions"},
}


def verify(directory):
    for suite, methods in REQUIRED.items():
        path = Path(directory) / f"TEST-{suite}.xml"
        if not path.is_file(): raise ValueError(f"Missing required PostgreSQL test report: {suite}")
        report = ET.parse(path).getroot()
        cases = {case.attrib["name"]: case for case in report.findall("testcase")}
        if not methods.issubset(cases): raise ValueError(f"Required PostgreSQL/schema scenario absent: {suite}")
        if any(report.findall(f".//{tag}") for tag in ("skipped", "failure", "error")):
            raise ValueError(f"Required PostgreSQL suite failed or was skipped: {suite}")
        if any(int(report.attrib.get(key, "0")) for key in ("skipped", "failures", "errors")):
            raise ValueError(f"Required PostgreSQL suite did not pass: {suite}")


if __name__ == "__main__":
    try:
        verify(sys.argv[1])
        print("Required PostgreSQL concurrency and fresh/populated schema comparisons passed")
    except (ValueError, OSError, ET.ParseError, IndexError) as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
