# How to run the tests here

Verified 2026-10-01 on a host with JDK 25 only (Lombok fails there, `known-errors.md` #4), Docker 29,
no Maven. Use the builder image with JDK 21 and give it the Docker socket for Testcontainers:

```bash
docker run --rm -v "$PWD":/src -w /src -v ~/.m2:/root/.m2 \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal --add-host=host.docker.internal:host-gateway \
  maven:3-eclipse-temurin-21 mvn -B clean verify
```

- `clean` always: `target/` of another branch skews coverage (concept T12).
- `verify` = tests + JaCoCo report (`target/site/jacoco/index.html`, `jacoco.csv`) + the coverage gate;
  `test` = tests and report without the gate.
- One class: `... mvn -B test -Dtest='BuilderCrossCheckTests'`. All but one:
  `-Dtest='!BuilderCrossCheckTests' -Dsurefire.failIfNoSpecifiedTests=false`.
- The container runs as root, so `target/` is root-owned afterwards; remove it with
  `docker run --rm -v "$PWD":/src alpine rm -rf /src/target` (or `sudo`).
- With a local JDK 21: `./mvnw -B clean verify` (Docker must be running).
- Coverage per package from the CSV: sum `LINE_MISSED/LINE_COVERED` and `BRANCH_MISSED/BRANCH_COVERED`
  over the rows of one `PACKAGE`.
- Do not run `massdatatest/` or `MassDataGenerator` (not a test; only on explicit request).
- The real CI run (`test.yml` on GitHub) is not verified by this; only the YAML syntax was checked.
