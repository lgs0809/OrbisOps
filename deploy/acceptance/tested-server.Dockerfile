# Reuse the pinned local runtime foundation; never advance it to every new app image.
ARG BASE_IMAGE
FROM ${BASE_IMAGE}
COPY --chown=orbisops:orbisops server/orbisops-app/target/orbisops-app.jar /opt/orbisops/orbisops.jar
COPY --chown=orbisops:orbisops server/scripts/mcp/ /opt/orbisops/scripts/mcp/
COPY --chown=orbisops:orbisops server/db/migrations/ /opt/orbisops/db/migrations/
COPY --chown=orbisops:orbisops server/scripts/db-migrate.sh /opt/orbisops/scripts/db-migrate.sh
