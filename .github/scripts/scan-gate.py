#!/usr/bin/env python3
"""Gate for the security scans of .github/workflows/security-scan.yml.

    scan-gate.py <kind> <head.json> [<base.json>]

kind: trivy-vuln | trivy-config | osv
  Without a base report every finding fails the gate (push, schedule, manual run).
  With a base report only findings the base does not have fail it (pull request): a pull request
  answers for what it adds, the debt of the base branch shows in the push and weekly runs.

Prints a Markdown table (also appended to $GITHUB_STEP_SUMMARY) and one ::error:: line per
failing finding. Exit 0 = pass, 1 = findings, 2 = unreadable report (never counted as clean).
Python standard library only; synced from eegfaktura-dev scripts/dev/ci/ (do not edit the copies).
"""
import json
import os
import sys

OSV_MIN_CVSS = 7.0  # HIGH and CRITICAL, as the Trivy scans (--severity HIGH,CRITICAL)
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
            key = (m.get("ID"), res.get("Target"))
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
    parse = (lambda f: osv(load(f), f)) if kind == "osv" else (lambda f: KINDS[kind](load(f)))
    head = parse(argv[2])
    base = parse(argv[3]) if len(argv) == 4 else None
    failing = {k: v for k, v in head.items() if base is None or k not in base}

    if base is None:
        title = f"{kind}: {len(head)} finding(s) HIGH/CRITICAL"
    else:
        fixed = len([k for k in base if k not in head])
        title = (f"{kind}: {len(failing)} new finding(s) in this pull request "
                 f"({len(head)} in head, {len(base)} in base, {fixed} fixed)")
    lines = [f"### {title}", ""]
    if failing:
        lines += ["| Severity | ID | Package / title | Fixed in | Where |", "|---|---|---|---|---|"]
        for row in sorted(failing.values()):
            lines.append("| " + " | ".join(str(c).replace("|", "\\|") for c in row) + " |")
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
    return 1 if failing else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
