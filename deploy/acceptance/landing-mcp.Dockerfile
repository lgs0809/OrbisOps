FROM orbisops/server:2.0.0
USER root
RUN mkdir -p /state /opt/landing-mcp && chown -R 10001:10001 /state /opt/landing-mcp
COPY --chown=10001:10001 deploy/acceptance/landing-mcp.py /opt/landing-mcp/server.py
COPY --chown=10001:10001 deploy/acceptance/order_request_observation.py /opt/landing-mcp/order_request_observation.py
COPY --chown=10001:10001 deploy/acceptance/landing_tool_contract.py deploy/acceptance/deployment_control.py /opt/landing-mcp/
USER 10001:10001
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
ENTRYPOINT ["python3", "/opt/landing-mcp/server.py"]
