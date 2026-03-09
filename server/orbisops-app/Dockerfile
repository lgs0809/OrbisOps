FROM maven:3.9.9-eclipse-temurin-17 AS builder
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -pl orbisops-app -am -Dmaven.test.skip=true package \
    && cp orbisops-app/target/orbisops-app.jar /tmp/orbisops.jar

FROM eclipse-temurin:17-jre-jammy AS runner

ARG NODE_MAJOR=20

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl gnupg \
    && mkdir -p /etc/apt/keyrings \
    && curl -fsSL https://deb.nodesource.com/gpgkey/nodesource-repo.gpg.key \
       | gpg --dearmor -o /etc/apt/keyrings/nodesource.gpg \
    && echo "deb [signed-by=/etc/apt/keyrings/nodesource.gpg] https://deb.nodesource.com/node_${NODE_MAJOR}.x nodistro main" \
       > /etc/apt/sources.list.d/nodesource.list \
    && apt-get update \
    && apt-get install -y --no-install-recommends nodejs git default-mysql-client postgresql-client \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd --system --gid 10001 orbisops \
    && useradd --system --uid 10001 --gid orbisops --home-dir /opt/orbisops --create-home orbisops \
    && mkdir -p /opt/orbisops/data /var/lib/orbisops/repair /opt/orbisops/scripts /opt/orbisops/db \
    && chown -R orbisops:orbisops /opt/orbisops /var/lib/orbisops

WORKDIR /opt/orbisops
COPY --from=builder --chown=orbisops:orbisops /tmp/orbisops.jar /opt/orbisops/orbisops.jar
COPY --chown=orbisops:orbisops scripts/mcp/ /opt/orbisops/scripts/mcp/
COPY --chown=orbisops:orbisops scripts/db-migrate.sh /opt/orbisops/scripts/db-migrate.sh
COPY --chown=orbisops:orbisops db/migrations/ /opt/orbisops/db/migrations/

USER orbisops
EXPOSE 8099

ENV JAVA_OPTS="" \
    PARAMS="" \
    TZ="UTC"

HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=8 \
  CMD curl --fail --silent http://127.0.0.1:8099/actuator/health >/dev/null || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /opt/orbisops/orbisops.jar $PARAMS"]
