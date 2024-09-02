FROM eclipse-temurin:21.0.2_13-jdk AS jre-build
RUN apt -y update && \
    apt -y upgrade

# add the jar file
COPY fhir-validator.jar ./fhir-validator.jar

# extract it
RUN jar xf fhir-validator.jar

# use jdeps to get all the dependencies so we can have a custom runtime
RUN jdeps --ignore-missing-deps -q  \
    --recursive  \
    --multi-release 21  \
    --print-module-deps  \
    --class-path 'BOOT-INF/lib/*'  \
    fhir-validator.jar > deps.info

# Create a custom Java runtime using the dependency list we created above
RUN jlink \
    --add-modules $(cat deps.info) \
    --strip-debug \
    --compress 2 \
    --no-header-files \
    --no-man-pages \
    --output /javaruntime

# now actually create the runtime image we want
FROM ubuntu:22.04
RUN apt -y update && \
    apt -y upgrade
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"
COPY --from=jre-build /javaruntime $JAVA_HOME

WORKDIR /app

COPY fhir-validator.jar ./fhir-validator.jar
RUN chmod -R a+x /app

USER nobody

#AEA-1024: Setting TEST_SYSTEM_PROP_VALIDATION_RESOURCE_CACHES_MS to max long so our resource cache never expires.
CMD ["java", "-Xms3000m", "-Xmx3000m", "-DTEST_SYSTEM_PROP_VALIDATION_RESOURCE_CACHES_MS=9223372036854775807", "-jar", "fhir-validator.jar"]
