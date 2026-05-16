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

FROM eclipse-temurin:21
# Version-Glob: passt zu jedem `eegfaktura-billing-*.jar`, robust gegen pom.xml-Bumps.
COPY --from=builder /build/target/eegfaktura-billing-*.jar /opt/app/eegfaktura-billing.jar
WORKDIR /opt/app
ENTRYPOINT ["java","-jar","/opt/app/eegfaktura-billing.jar"]
