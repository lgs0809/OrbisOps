FROM orbisops/server:2.0.0
USER root
RUN mkdir -p /state /opt/observability-mcp && chown -R 10001:10001 /state /opt/observability-mcp
COPY --chown=10001:10001 deploy/acceptance/observability-mcp.py /opt/observability-mcp/server.py
COPY --chown=10001:10001 deploy/acceptance/observability_scope.py /opt/observability-mcp/observability_scope.py
COPY --chown=10001:10001 deploy/acceptance/observability_ledger.py /opt/observability-mcp/observability_ledger.py
COPY --chown=10001:10001 deploy/acceptance/native_change_evidence.py /opt/observability-mcp/native_change_evidence.py
USER 10001:10001
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
ENTRYPOINT ["python3", "/opt/observability-mcp/server.py"]
