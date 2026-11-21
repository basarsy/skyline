# ── Build: install with the lockfile, then build the static app ──────────────
FROM node:24-alpine AS build
WORKDIR /app
RUN corepack enable && corepack prepare pnpm@10.34.9 --activate

COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile

COPY . .
# Relative API base: nginx proxies /api to the gateway, so the browser stays same-origin.
ARG VITE_API_BASE=/api/v1
ENV VITE_API_BASE=${VITE_API_BASE}
RUN pnpm build

# ── Serve: static files + /api proxy ──────────────────────────────────────────
FROM nginx:1.29-alpine AS runtime
# Upstream for /api; override at run time (e.g. a different host or port for the gateway).
ENV SKYLINE_GATEWAY_URL=http://gateway-service:8000
COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=10s --timeout=3s --retries=3 CMD wget -qO- http://127.0.0.1/healthz || exit 1
