#!/usr/bin/env python3
"""Gate for the security scans of .github/workflows/security-scan.yml.

    scan-gate.py <kind> <head.json> [<base.json>]

kind: trivy-vuln | trivy-config | osv
  Without a base report every finding fails the gate (push, schedule, manual run).
  With a base report only findings the base does not have fail it (pull request): a pull request
  answers for what it adds, the debt of the base branch shows in the push and weekly runs.

Which severities fail the gate: $SCAN_FAIL_ON = HIGH (default: HIGH and CRITICAL), CRITICAL
(only CRITICAL; Trivy CRITICAL, OSV CVSS >= 9.0) or none (report only: nothing fails — a temporary
setting while the legacy debt is paid down; every run says so). Findings that do not fail are listed
and annotated as warnings, never hidden.

Prints a Markdown table (also appended to $GITHUB_STEP_SUMMARY) and one ::error:: (failing) or
::warning:: (reported only) line per finding. Exit 0 = pass, 1 = failing findings, 2 = unreadable
report or bad $SCAN_FAIL_ON (never counted as clean).
Python standard library only; synced from eegfaktura-dev scripts/dev/ci/ (do not edit the copies).
"""
import json
import os
import sys

OSV_MIN_CVSS = 7.0  # HIGH and CRITICAL, as the Trivy scans (--severity HIGH,CRITICAL)
OSV_CRITICAL_CVSS = 9.0  # CVSS v3 "critical"


def is_critical(row):
    sev = str(row[0])
    if sev.startswith("CVSS "):
        return float(sev[5:]) >= OSV_CRITICAL_CVSS
    return sev == "CRITICAL"
UNSCORED = {}  # report path -> OSV groups without a CVSS score (shown, not gated)


def load(path):
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    except (OSError, ValueError) as e:
        print(f"::error::unreadable scan report {path}: {e}")
        sys.exit(2)


def trivy_vuln(report):
    """{key: row} of HIGH/CRITICAL vulnerabilities; the key ignores the path (lines move)."""
    found = {}
    for res in report.get("Results") or []:
        for v in res.get("Vulnerabilities") or []:
            key = (v.get("VulnerabilityID"), v.get("PkgName"), v.get("InstalledVersion"))
            found[key] = (v.get("Severity", ""), v.get("VulnerabilityID", ""),
                          f'{v.get("PkgName")} {v.get("InstalledVersion")}',
                          v.get("FixedVersion") or "-", res.get("Target", ""))
    return found


def trivy_config(report):
    found = {}
    for res in report.get("Results") or []:
        for m in res.get("Misconfigurations") or []:
            if m.get("Status") != "FAIL":
                continue
            # the message names the resource, so a second instance in the same file counts on its own
            key = (m.get("ID"), res.get("Target"), m.get("Message"))
            found[key] = (m.get("Severity", ""), m.get("ID", ""), m.get("Title", ""), "-",
                          res.get("Target", ""))
    return found


def osv(report, report_file=""):
    if not isinstance(report.get("results", []), list):
        print("::error::unexpected OSV-Scanner JSON (no results list)")
        sys.exit(2)
    found = {}
    for res in report.get("results") or []:
        path = (res.get("source") or {}).get("path", "")
        for pkg in res.get("packages") or []:
            p = pkg.get("package") or {}
            for g in pkg.get("groups") or []:
                try:
                    score = float(g.get("max_severity") or "")
                except ValueError:
                    # no CVSS score published (e.g. Go vulndb only): listed, not gated, like the
                    # mono pipeline — Trivy reports these as UNKNOWN and skips them as well
                    UNSCORED.setdefault(report_file, set()).add((p.get("name"), p.get("version"),
                                                          ", ".join(sorted(g.get("ids") or []))))
                    continue
                if score < OSV_MIN_CVSS:
                    continue
                ids = sorted(g.get("ids") or [])
                key = (p.get("ecosystem"), p.get("name"), p.get("version"), ids[0] if ids else "")
                found[key] = (f"CVSS {score:.1f}", ", ".join(ids),
                              f'{p.get("name")} {p.get("version")}', "-", path)
    return found


KINDS = {"trivy-vuln": trivy_vuln, "trivy-config": trivy_config, "osv": osv}


def main(argv):
    if len(argv) not in (3, 4) or argv[1] not in KINDS:
        print(__doc__)
        return 2
    kind = argv[1]
    fail_on = os.environ.get("SCAN_FAIL_ON", "HIGH")
    if fail_on not in ("HIGH", "CRITICAL", "none"):
        print(f"::error::SCAN_FAIL_ON must be HIGH, CRITICAL or none, not {fail_on!r}")
        return 2
    if fail_on == "none":
        print(f"::warning title={kind}::Security gate off (SCAN_FAIL_ON=none, temporary): findings are "
              "reported, none fails the run. It will be tightened again.")
    parse = (lambda f: osv(load(f), f)) if kind == "osv" else (lambda f: KINDS[kind](load(f)))
    head = parse(argv[2])
    base = parse(argv[3]) if len(argv) == 4 else None
    reported = {k: v for k, v in head.items() if base is None or k not in base}
    failing = {k: v for k, v in reported.items() if fail_on == "HIGH" or (fail_on == "CRITICAL" and is_critical(v))}
    warned = {k: v for k, v in reported.items() if k not in failing}

    if base is None:
        title = f"{kind}: {len(head)} finding(s) HIGH/CRITICAL, {len(failing)} failing (fails on {fail_on})"
    else:
        fixed = len([k for k in base if k not in head])
        title = (f"{kind}: {len(reported)} new finding(s) in this pull request, {len(failing)} failing "
                 f"(fails on {fail_on}; {len(head)} in head, {len(base)} in base, {fixed} fixed)")
    lines = [f"### {title}", ""]
    if reported:
        lines += ["| Gate | Severity | ID | Package / title | Fixed in | Where |", "|---|---|---|---|---|---|"]
        for k, row in sorted(reported.items(), key=lambda kv: (kv[0] not in failing, kv[1])):
            gate = "fails" if k in failing else "reported"
            lines.append("| " + " | ".join([gate] + [str(c).replace("|", "\\|") for c in row]) + " |")
    unscored = UNSCORED.get(argv[2], set()) - (UNSCORED.get(argv[3], set()) if base is not None else set())
    if unscored:
        lines += ["", f"<details><summary>{len(unscored)} {'new ' if base is not None else ''}advisory group(s) "
                  "without a CVSS score — not gated, check by hand</summary>", ""]
        lines += [f"- {n} {v}: {ids}" for n, v, ids in sorted(unscored)]
        lines += ["", "</details>"]
    lines.append("")
    text = "\n".join(lines)
    print(text)
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as f:
            f.write(text + "\n")
    for row in sorted(failing.values()):
        print(f"::error title={kind}::{row[0]} {row[1]} {row[2]} ({row[4]})")
    for row in sorted(warned.values()):
        print(f"::warning title={kind}::{row[0]} {row[1]} {row[2]} ({row[4]})")
    return 1 if failing else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
