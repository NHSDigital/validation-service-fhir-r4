FROM openjdk:11.0.8

WORKDIR /app

COPY target/fhir-validator-*.jar ./fhir-validator.jar
RUN chmod -R a+x /app

USER nobody

CMD ["java", "-Xms2500m", "-Xmx2500m", "-DTEST_SYSTEM_PROP_VALIDATION_RESOURCE_CACHES_MS=9223372036854775807", "-jar", "fhir-validator.jar"]
