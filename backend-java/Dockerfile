# syntax=docker/dockerfile:1.7
ARG MAVEN_IMAGE=maven:3.9.11-eclipse-temurin-17
ARG JAVA_IMAGE=eclipse-temurin:17-jre-jammy
FROM ${MAVEN_IMAGE} AS builder
ARG MODULE
ARG MAIN_CLASS
ENV MAVEN_OPTS="-Xmx512m -XX:MaxMetaspaceSize=256m"
WORKDIR /build
COPY pom.xml ./
COPY ml-common ./ml-common
COPY ml-gateway ./ml-gateway
COPY ml-user ./ml-user
COPY ml-course ./ml-course
COPY ml-sale ./ml-sale
COPY ml-order ./ml-order
COPY ml-barrage ./ml-barrage
COPY ml-agent-relay ./ml-agent-relay
COPY ml-ai-search ./ml-ai-search
COPY tools ./tools
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -pl "${MODULE}" -am -DskipTests install \
    && mvn -B -pl "${MODULE}" dependency:copy-dependencies -DoutputDirectory=/build/lib
RUN cp "${MODULE}/target/${MODULE}-1.0-SNAPSHOT.jar" /build/app.jar

FROM ${JAVA_IMAGE}
ARG MAIN_CLASS
RUN useradd --system --create-home mylesson
WORKDIR /app
COPY --from=builder /build/app.jar /app/app.jar
COPY --from=builder /build/lib /app/lib
ENV MAIN_CLASS=${MAIN_CLASS}
USER mylesson
ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS:--Xms64m -Xmx256m} -cp '/app/app.jar:/app/lib/*' \"$MAIN_CLASS\""]
