# ---- build stage ----
FROM node:22-alpine AS build
WORKDIR /app

# Cache dependency downloads
COPY package.json package-lock.json ./
RUN npm ci

COPY . .

# Public HTTPS URL of the mobile backend (phone+password JWT), baked into the
# web bundle at build time. Required for a deployed web frontend; leave unset
# only when the browser can reach the backend at whatever value EXPO_PUBLIC_
# BACKEND_URL resolves to (empty -> http://localhost:8080 fallback).
ARG EXPO_PUBLIC_BACKEND_URL=
ENV EXPO_PUBLIC_BACKEND_URL=$EXPO_PUBLIC_BACKEND_URL \
    CI=1

RUN npx expo export --platform web --clear

# ---- runtime stage ----
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80

# nginx:alpine ships no curl, so the check uses the busybox wget its base
# provides. Without a healthcheck the container reports no health state at all,
# which anything waiting on a healthy dependency (or Coolify's per-service wait)
# treats as a failure.
HEALTHCHECK --interval=10s --timeout=5s --start-period=30s --start-interval=5s --retries=3 \
  CMD wget -q -O /dev/null http://127.0.0.1/ || exit 1