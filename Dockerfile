# syntax=docker/dockerfile:1
FROM eclipse-temurin:23.0.1_11-jdk-alpine-3.21 AS jre-build
RUN apk update; \
    apk upgrade

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
FROM alpine:3.21.2 AS runtime

ARG VALIDATOR_VERSION=unknown
ARG COMMIT_SHA=unknown

RUN apk update; \
    apk upgrade
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"
COPY --from=jre-build /javaruntime $JAVA_HOME

WORKDIR /app

COPY fhir-validator.jar ./fhir-validator.jar
RUN chmod -R a+x /app

USER nobody
ENV validatorVersion=$VALIDATOR_VERSION
ENV commitSha=$COMMIT_SHA

#AEA-1024: Setting TEST_SYSTEM_PROP_VALIDATION_RESOURCE_CACHES_MS to max long so our resource cache never expires.
CMD ["java", "-Xms3000m", "-Xmx3000m", "-DTEST_SYSTEM_PROP_VALIDATION_RESOURCE_CACHES_MS=9223372036854775807", "-jar", "fhir-validator.jar"]
