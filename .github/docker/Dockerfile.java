# syntax=docker/dockerfile:1
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY desk-common/ desk-common/
COPY desk-api/ desk-api/
COPY desk-gateway/ desk-gateway/
COPY desk-user/ desk-user/
COPY desk-ticket/ desk-ticket/
COPY desk-knowledge/ desk-knowledge/
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp verify

FROM eclipse-temurin:21-jre AS runtime
ARG DESK_MODULE
RUN case "$DESK_MODULE" in desk-gateway|desk-user|desk-ticket|desk-knowledge) ;; *) exit 1 ;; esac \
    && groupadd --gid 10001 desk \
    && useradd --uid 10001 --gid desk --create-home desk \
    && mkdir -p /app /var/log/service-desk \
    && chown desk:desk /app /var/log/service-desk
WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/${DESK_MODULE}/target/${DESK_MODULE}-0.1.0-SNAPSHOT.jar /app/application.jar
ENV SPRING_PROFILES_ACTIVE=prod \
    DESK_BIND_ADDRESS=0.0.0.0 \
    DESK_LOG_DIRECTORY=/var/log/service-desk \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -Dfile.encoding=UTF-8 -Duser.timezone=UTC -Dcsp.sentinel.log.dir=/var/log/service-desk/sentinel -Dcom.alibaba.nacos.config.log.dir=/var/log/service-desk/nacos -DJM.LOG.PATH=/var/log/service-desk/nacos -Drocketmq.client.logRoot=/var/log/service-desk/rocketmq"
USER 10001:10001
ENTRYPOINT ["java", "-jar", "/app/application.jar"]
