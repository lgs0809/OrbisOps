FROM orbisops/web:2.0.0
# Verified static files and the source nginx template; no runtime volumes are modified.
RUN rm -rf /usr/share/nginx/html/*
COPY web/dist/ /usr/share/nginx/html/

COPY web/nginx.docker.conf /etc/nginx/templates/default.conf.template
COPY --chmod=755 web/15-model-response-timeout.envsh /docker-entrypoint.d/15-model-response-timeout.envsh
