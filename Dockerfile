FROM eclipse-temurin:21
COPY target/eegfaktura-billing-0.1.21.jar /tmp
WORKDIR /tmp
ENTRYPOINT ["java","-jar","eegfaktura-billing-0.1.21.jar"]
