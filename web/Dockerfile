FROM node:20-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

FROM node:20-alpine AS builder
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .
ARG ORBISOPS_API_BASE_URL=""
ENV ORBISOPS_API_BASE_URL=${ORBISOPS_API_BASE_URL}
RUN npm run build

FROM nginx:1.27-alpine AS runner
WORKDIR /usr/share/nginx/html
RUN rm -rf ./*
COPY --from=builder /app/dist ./
COPY nginx.docker.conf /etc/nginx/templates/default.conf.template

ENV ORBISOPS_BACKEND_UPSTREAM=http://backend:8099
EXPOSE 3002

HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=5 \
  CMD wget -q -O /dev/null http://127.0.0.1:3002/ || exit 1

CMD ["nginx", "-g", "daemon off;"]
