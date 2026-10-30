# Multi-Stage Build: Maven-Builder + Temurin-Runtime
# Vermeidet das Pre-Build-JAR-Coupling — das target/-Verzeichnis ist .gitignored,
# also kann ein Source-Build-Workflow nicht direkt das alte single-stage
# `COPY target/...jar` nutzen.

FROM maven:3-eclipse-temurin-21 AS builder
WORKDIR /build

# Dependencies separat cachen (langsamer initial-build, schneller bei Source-Changes)
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline -DskipTests

COPY src ./src
RUN mvn -B -ntp clean package -DskipTests

# JRE statt JDK (kleineres Image, weniger CVE-Surface).
FROM eclipse-temurin:21-jre-jammy

LABEL org.opencontainers.image.title="eegfaktura-billing"
LABEL org.opencontainers.image.description="EEG Faktura Abrechnungs-Service (Rechnungen, Gutschriften, SEPA, Mail-Versand)"
LABEL org.opencontainers.image.vendor="Verein zur Förderung von Erneuerbaren Energiegemeinschaften"
LABEL org.opencontainers.image.licenses=AGPL-3.0

# Text is measured with the JDK's "SansSerif", which fontconfig maps to the first sans-serif font - in the base image
# DejaVu Sans, wider than Helvetica, the font the PDF is written in. Jasper then reserved a second, empty line for
# texts that fit in Helvetica (pilot feedback 20.10.2026: blank line below "Zählpunkt AT…"). Liberation Sans has
# Helvetica's glyph widths; preferring it for sans-serif makes measuring match printing. The PDF fonts stay
# Helvetica/Helvetica-Bold (Jasper's default font must stay SansSerif, a named font loses the bold PDF face).
COPY docker/fontconfig-local.conf /etc/fonts/local.conf
RUN apt-get update \
    && apt-get install -y --no-install-recommends fonts-liberation \
    && rm -rf /var/lib/apt/lists/* \
    && fc-cache -f

# Non-root runtime user (Pod-Security-Admission "restricted" + Defense-in-Depth).
RUN groupadd -r app --gid 1001 \
    && useradd -r -g app --uid 1001 -s /sbin/nologin -M app

# Version-Glob: passt zu jedem `eegfaktura-billing-*.jar`, robust gegen pom.xml-Bumps.
COPY --from=builder /build/target/eegfaktura-billing-*.jar /opt/app/eegfaktura-billing.jar
WORKDIR /opt/app

# Numerische UID:GID statt Username: kubelet kann runAsNonRoot nur bei
# numerischer USER-Direktive verifizieren (CreateContainerConfigError sonst).
USER 1001:1001

# service.version of every log line; CI passes metadata-action's version (release tag, or branch name)
ARG SERVICE_VERSION=dev
ENV SERVICE_VERSION=${SERVICE_VERSION}

ENTRYPOINT ["java","-jar","/opt/app/eegfaktura-billing.jar"]
