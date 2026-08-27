FROM python:3.12-slim
RUN mkdir -p /state /app && chown -R 10001:10001 /state /app
COPY --chown=10001:10001 scripts/fixtures/mcp-acceptance-server.py /app/server.py
USER 10001:10001
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
ENTRYPOINT ["python", "/app/server.py", "--database", "/state/acceptance.sqlite", "--host", "0.0.0.0", "--port", "8181"]
