FROM openjdk:11.0.8

WORKDIR /app

COPY target/fhir-validator-*.jar ./fhir-validator.jar
RUN chmod -R a+x /app

USER nobody

CMD ["java", "-jar", "fhir-validator.jar"]
