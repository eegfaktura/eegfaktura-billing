# Multi-Stage Build: Maven-Builder + Temurin-Runtime
# Vermeidet das Pre-Build-JAR-Coupling — das target/-Verzeichnis ist .gitignored,
# also kann ein Source-Build-Workflow nicht direkt das alte single-stage
# `COPY target/...jar` nutzen.

FROM maven:3-eclipse-temurin-26 AS builder
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

# Non-root runtime user (Pod-Security-Admission "restricted" + Defense-in-Depth).
RUN groupadd -r app --gid 1001 \
    && useradd -r -g app --uid 1001 -s /sbin/nologin -M app

# Version-Glob: passt zu jedem `eegfaktura-billing-*.jar`, robust gegen pom.xml-Bumps.
COPY --from=builder /build/target/eegfaktura-billing-*.jar /opt/app/eegfaktura-billing.jar
WORKDIR /opt/app

# Numerische UID:GID statt Username: kubelet kann runAsNonRoot nur bei
# numerischer USER-Direktive verifizieren (CreateContainerConfigError sonst).
USER 1001:1001

ENTRYPOINT ["java","-jar","/opt/app/eegfaktura-billing.jar"]
