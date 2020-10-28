FROM openjdk:11.0.8

WORKDIR /app

COPY target/fhir-validator-*.jar ./fhir-validator.jar
RUN chmod -R a+x /app

USER nobody

CMD ["java", "-Xms1500m", "-Xmx1500m", "-jar", "fhir-validator.jar"]
