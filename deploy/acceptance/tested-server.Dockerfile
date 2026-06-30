# Do not use the output image as its own base: each deployment would retain another old JAR.
# This locally prepared foundation has the same runtime, user, entrypoint and database clients.
FROM orbisops/server:2.0.0
COPY --chown=orbisops:orbisops server/orbisops-app/target/orbisops-app.jar /opt/orbisops/orbisops.jar
COPY --chown=orbisops:orbisops server/scripts/mcp/ /opt/orbisops/scripts/mcp/
COPY --chown=orbisops:orbisops server/db/migrations/ /opt/orbisops/db/migrations/
COPY --chown=orbisops:orbisops server/scripts/db-migrate.sh /opt/orbisops/scripts/db-migrate.sh
