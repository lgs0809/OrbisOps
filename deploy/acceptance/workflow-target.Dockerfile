FROM orbisops/server:2.0.0
USER root
RUN mkdir -p /state /opt/workflow-target && chown -R 10001:10001 /state /opt/workflow-target
COPY --chown=10001:10001 deploy/acceptance/workflow-target.py /opt/workflow-target/server.py
COPY --chown=10001:10001 deploy/acceptance/deployment_control.py /opt/workflow-target/deployment_control.py
USER 10001:10001
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
ENTRYPOINT ["python3", "/opt/workflow-target/server.py"]
