ARG NODE_IMAGE=node:22.12-bookworm-slim
ARG NGINX_IMAGE=nginx:1.27-alpine
FROM ${NODE_IMAGE} AS builder

ENV NODE_OPTIONS="--max-old-space-size=1024"
WORKDIR /build
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM ${NGINX_IMAGE}
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /build/dist /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1/healthz || exit 1
