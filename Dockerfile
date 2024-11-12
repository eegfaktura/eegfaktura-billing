FROM eclipse-temurin:17
COPY target/eegfaktura-billing-0.1.20.jar /tmp
WORKDIR /tmp
ENTRYPOINT ["java","-jar","eegfaktura-billing-0.1.20.jar"]
