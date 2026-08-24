# syntax=docker/dockerfile:1

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY monolith/pom.xml pom.xml
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp dependency:go-offline
COPY monolith/src src
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp clean package -DskipTests
RUN java -Djarmode=tools -jar target/monolith.jar extract --layers --launcher --destination extracted

FROM eclipse-temurin:21-jre AS runtime
RUN apt-get update \
	&& apt-get install --no-install-recommends -y curl \
	&& rm -rf /var/lib/apt/lists/* \
	&& groupadd --system --gid 1001 rfq \
	&& useradd --system --uid 1001 --gid rfq --create-home rfq
WORKDIR /app
COPY --from=build --chown=rfq:rfq /workspace/extracted/dependencies/ ./
COPY --from=build --chown=rfq:rfq /workspace/extracted/spring-boot-loader/ ./
COPY --from=build --chown=rfq:rfq /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=rfq:rfq /workspace/extracted/application/ ./
USER rfq
ENV SERVER_PORT=8080 \
	JAVA_OPTS=""
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
	CMD curl -fsS "http://localhost:${SERVER_PORT}/actuator/health" || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
