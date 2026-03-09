FROM eclipse-temurin:17-jre-jammy

ARG NODE_MAJOR=20

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl gnupg \
    && mkdir -p /etc/apt/keyrings \
    && curl -fsSL https://deb.nodesource.com/gpgkey/nodesource-repo.gpg.key \
       | gpg --dearmor -o /etc/apt/keyrings/nodesource.gpg \
    && echo "deb [signed-by=/etc/apt/keyrings/nodesource.gpg] https://deb.nodesource.com/node_${NODE_MAJOR}.x nodistro main" \
       > /etc/apt/sources.list.d/nodesource.list \
    && apt-get update \
    && apt-get install -y --no-install-recommends nodejs \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd --system --gid 10001 orbisops \
    && useradd --system --uid 10001 --gid orbisops --home-dir /opt/orbisops --create-home orbisops \
    && mkdir -p /opt/orbisops/data \
    && chown -R orbisops:orbisops /opt/orbisops

WORKDIR /opt/orbisops
COPY --chown=orbisops:orbisops target/orbisops-app.jar /opt/orbisops/orbisops.jar

USER orbisops
EXPOSE 8099

ENV JAVA_OPTS="" \
    PARAMS="" \
    TZ="UTC"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /opt/orbisops/orbisops.jar $PARAMS"]
