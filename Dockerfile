FROM openjdk:17.0.2
COPY target/eegfaktura-billing-0.1.10.jar /tmp
WORKDIR /tmp
ENTRYPOINT ["java","-jar","eegfaktura-billing-0.1.10.jar"]
