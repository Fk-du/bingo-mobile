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