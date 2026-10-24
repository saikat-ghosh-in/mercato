# Dependency verification — 24 October 2026

The resolved application uses Spring Boot 3.5.16, with compatible Jackson BOM 2.21.8, Tomcat 10.1.61 and PostgreSQL JDBC 42.7.14 overrides. These artifacts were verified on Maven Central. The previous Spring Security 6.5.7 falls in the affected range for [CVE-2026-22732](https://spring.io/security/cve-2026-22732/); the updated managed 6.5.11 includes that fix. A major Spring/Boot migration is not part of this revision.

The CycloneDX 2.9.1 plugin generated the resolved direct/transitive Java SBOM. Official Trivy 0.76.1 was downloaded from its release and verified against the vendor SHA256 `1051f49cb22d5284f43425f72aad9cda63ca12ceb92e7caa06903490c82dc9bf` for the Windows archive. Scanning with the current database and the checked-in version-specific OpenVEX statements exited 0 with **no unassessed HIGH/CRITICAL findings**. This is not a claim that the unfiltered component scan contains no advisories.

Two Spring MVC 6.2.19 findings remain assessed as not affected:

- [CVE-2026-47884](https://spring.io/security/cve-2026-47884/) requires XSLT view rendering and a wildcard view mapping. Mercato has REST controllers and a separate React frontend, with no XSLT view resolver.
- [CVE-2026-47890](https://spring.io/security/cve-2026-47890/) requires SSE with view fragments. Mercato exposes no such endpoint. The vendor classifies these as medium/low respectively, although the scanner's selected source rated them critical.

`security/java-not-affected.openvex.json` is limited to those CVEs and the exact Maven component version. It does not globally ignore CVEs or unfixed packages. `CookieSessionIntegrationTest.restApiCannotActivateTheVexExcludedViewRenderingFeatures` checks the actual controller/resolver registrations and blocks introduction of the affected rendering features. Reassess the statements when adding server-rendered views/SSE or changing the framework version. `security/trivy.yaml` applies the same statements in CI; scanner/database failures and other HIGH/CRITICAL findings fail CI.

The final npm audit reports **0 vulnerabilities** after compatible lockfile updates, including a newly reported transitive Zod advisory fixed during final verification. Install scripts were disabled during remediation. Frontend tests, lint and production build were rerun after the final change.

Local ignored evidence: `backend/target/bom.json`, `java-advisories-final.json`, `java-advisories-final.log`, `npm-audit-final.json`, and verification logs. CI regenerates the SBOM and current scans; it does not trust these historical local files. There are no credentials or customer rows in the SBOM.

25 October follow-up: the first GitHub scan exposed a configuration difference from the explicit local CLI run. Trivy's config requires `vulnerability.vex`; the old root-level `vex` key was ignored. The failure was reproduced with the verified local scanner and cached database (the two exact assessed CVEs remained, exit 1), then corrected without changing any assessment (zero unassessed HIGH/CRITICAL findings, exit 0). The full runtime view-registration guard also passed in PostgreSQL-backed GitHub CI. [Trivy configuration schema](https://github.com/aquasecurity/trivy/blob/main/schema/trivy-config.json).
